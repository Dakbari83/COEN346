#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <sys/wait.h>

#include "expansion_args.h"
#include "io_redirections.h"
#include "jobs.h"
#include "parser.h"
#include "signal_child.h"
#include "utils.h"

/**
 * @brief Closes both ends of every pipe
 * @param pipes the pipes, [0] is the read end and [1] the write end
 * @param count number of pipes
 */
void close_pipes(int pipes[][2], int count)
{
    for (int i = 0; i < count; i++)
    {
        close(pipes[i][0]);
        close(pipes[i][1]);
    }
}

/**
 * @brief In a child process: replaces the process with the program. Never returns.
 * @param cmd name of the program, NULL if every word expanded to nothing
 * @param args the command and its arguments, the last item is a null pointer
 */
void run_program(char *cmd, char **args)
{
    sigchld_mask(SIG_UNBLOCK);
    if (cmd == NULL)
        exit(EXIT_SUCCESS);

    execvp(cmd, args);
    perror(cmd);
    exit(EXIT_FAILURE);
}

/**
 * @brief Parent side once processes are started: background processes are added to the jobs
 * list, foreground ones are waited for. SIGCHLD, blocked by the caller, is unblocked here.
 * @param pids pid of each process, or -1 for a process that could not be started
 * @param seq the command of each process
 * @param count number of processes
 * @param bg 1 if the processes run in the background
 */
void finish_processes_init(pid_t *pids, char ***seq, int count, int bg)
{
    if (bg)
    {
        for (int i = 0; i < count; i++)
        {
            if (pids[i] > 0)
                add_job(pids[i], seq[i]);
        }
    }

    // Unblocked before waiting, so background messages still appear during a foreground wait
    sigchld_mask(SIG_UNBLOCK);

    if (!bg)
    {
        for (int i = 0; i < count; i++)
        {
            if (waitpid(pids[i], NULL, 0) < 0)
                perror("waitpid");
        }
    }
}

/**
 * @brief Executes a single, simple command in another process
 * @param cmd name of the program to run
 * @param args the command and its arguments, the last item is a null pointer
 * @param in file descriptor to use as standard input (STDIN_FILENO if not redirected)
 * @param out file descriptor to use as standard output (STDOUT_FILENO if not redirected)
 * @param bg 1 to run in the background, 0 to wait until the command finishes
 * @return EXIT_SUCCESS, or EXIT_FAILURE if the process cannot be started
 */
int execute_command(char *cmd, char **args, int in, int out, int bg)
{
    if (bg && !has_room_for_jobs(1))
        return EXIT_FAILURE;

    sigchld_mask(SIG_BLOCK);
    fflush(stdout);

    pid_t pid = fork();

    if (pid < 0)
    {
        perror("Fork failed.");
        sigchld_mask(SIG_UNBLOCK);
        return EXIT_FAILURE;
    }
    if (pid == 0)
    {
        redirect_to_files(in, out);
        close_redirections_files(in, out);
        run_program(cmd, args);
    }

    finish_processes_init(&pid, &args, 1, bg);
    return EXIT_SUCCESS;
}

/**
 * @brief Runs commands connected by pipes: seq[0] | seq[1] | ... | seq[ncommands - 1]
 * @param seq the commands (at least two), the last item is a null pointer
 * @param ncommands number of commands
 * @param in input file descriptor for the first command
 * @param out output file descriptor for the last command
 * @param bg 1 to run the pipeline in the background
 * @return EXIT_SUCCESS, or EXIT_FAILURE if the pipes cannot be created
 */
int execute_pipe(char ***seq, int ncommands, int in, int out, int bg)
{
    int npipes = ncommands - 1;
    int pipes[npipes][2];
    pid_t pids[ncommands];

    if (bg && !has_room_for_jobs(ncommands))
        return EXIT_FAILURE;

    for (int i = 0; i < npipes; i++)
    {
        if (pipe(pipes[i]) < 0)
        {
            perror("Failed to create pipe.");
            close_pipes(pipes, i);
            return EXIT_FAILURE;
        }
    }

    // Until the jobs are in the list, a fast child must not trigger the handler
    sigchld_mask(SIG_BLOCK);
    fflush(stdout);

    for (int i = 0; i < ncommands; i++)
    {
        pids[i] = fork();

        if (pids[i] < 0)
        {
            perror("Fork failed.");
            close_pipes(pipes, npipes);
            sigchld_mask(SIG_UNBLOCK);
            return EXIT_FAILURE;
        }
        else if (pids[i] == 0)
        {
            // Command i reads from the previous pipe and writes into the next one
            redirect_to_files(i == 0 ? in : pipes[i - 1][0], i == npipes ? out : pipes[i][1]);
            close_pipes(pipes, npipes);
            close_redirections_files(in, out);
            run_program(seq[i][0], seq[i]);
        }
    }

    // A command only sees the end of its input once every write end of its pipe is closed
    close_pipes(pipes, npipes);
    finish_processes_init(pids, seq, ncommands, bg);
    return EXIT_SUCCESS;
}

/**
 * @brief Executes a command pipeline.
 * @param l A pointer to a cmdline structure containing the parsed command.
 * @return 0 on success, or a non-zero value on error.
 */
int execute(struct cmdline *l)
{
    if (l->seq[0] == NULL)
        return EXIT_SUCCESS;

    // Built-in: the jobs list lives in the shell itself, so it must not run in a child
    if (strcmp(l->seq[0][0], "jobs") == 0)
    {
        print_jobs();
        return EXIT_SUCCESS;
    }

    int in, out;
    if (open_redirections_files(l, &in, &out) < 0)
        return EXIT_FAILURE;

    int ncommands = 0;
    while (l->seq[ncommands] != NULL)
        ncommands++;

    char **seq[ncommands + 1];
    for (int i = 0; i < ncommands; i++)
        seq[i] = expand_args(l->seq[i]);
    seq[ncommands] = NULL;

    int result = ncommands == 1
                     ? execute_command(seq[0][0], seq[0], in, out, l->bg)
                     : execute_pipe(seq, ncommands, in, out, l->bg);

    for (int i = 0; i < ncommands; i++)
        free_args(seq[i]);
    close_redirections_files(in, out); // Make sure the redirections fd are closed in the parent.

    return result;
}