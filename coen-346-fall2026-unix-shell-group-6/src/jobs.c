#include <signal.h>
#include <stdio.h>
#include <string.h>

#include "jobs.h"
#include "signal_child.h"

struct Job CURRENT_JOBS[MAX_JOBS];
int CURRENT_JOBS_COUNT = 0;

/**
 * @brief Built-in "jobs": prints the background processes still running
 */
void print_jobs(void)
{
    sigchld_mask(SIG_BLOCK);
    for (int i = 0; i < CURRENT_JOBS_COUNT; i++)
    {
        printf("[%d] %s\n", CURRENT_JOBS[i].pid, CURRENT_JOBS[i].command);
    }
    sigchld_mask(SIG_UNBLOCK);
}

/**
 * @brief Checks that more background processes fit in the jobs list
 * @param count number of processes to add
 * @return 1 if they fit, 0 otherwise (an error message is printed)
 */
int has_room_for_jobs(int count)
{
    if (CURRENT_JOBS_COUNT + count <= MAX_JOBS)
        return 1;
    perror("Too many background jobs.");
    return 0;
}

/**
 * @brief Adds a background process to the jobs list and records its start time.
 * SIGCHLD must be blocked by the caller, and the list must have room (has_room_for_jobs).
 * @param pid pid of the background process
 * @param args the command and its arguments, the last item is a null pointer
 */
void add_job(pid_t pid, char **args)
{
    struct Job *job = &CURRENT_JOBS[CURRENT_JOBS_COUNT];
    job->pid = pid;
    gettimeofday(&job->start, NULL);

    job->command[0] = '\0';
    for (int i = 0; args[i] != NULL; i++)
    {
        // Room for the word, a space and the final '\0'; a longer command is cut
        if (strlen(job->command) + strlen(args[i]) + 2 > MAX_COMMAND_LENGTH)
            break;
        strcat(job->command, args[i]);
        strcat(job->command, " ");
    }
    CURRENT_JOBS_COUNT++;
}
