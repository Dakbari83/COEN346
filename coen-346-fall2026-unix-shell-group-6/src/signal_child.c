
#include <errno.h>
#include <signal.h>
#include <stdio.h>
#include <sys/time.h>
#include <sys/wait.h>

#include "jobs.h"
#include "shell.h"
#include "signal_child.h"

/**
 * @brief Blocks or unblocks SIGCHLD, so the handler cannot change the jobs list while it is used
 * @param how SIG_BLOCK or SIG_UNBLOCK
 */
void sigchld_mask(int how)
{
    sigset_t set;
    sigemptyset(&set);
    sigaddset(&set, SIGCHLD);
    if (sigprocmask(how, &set, NULL) < 0)
        perror("sigprocmask");
}

/**
 * @brief SIGCHLD handler, runs as soon as a child terminates: removes the finished background
 * processes from the jobs list and prints their computation time in blue
 * @param sig the signal number (unused, always SIGCHLD)
 */
static void sigchld_handler(int sig)
{
    (void)sig;
    sigchld_mask(SIG_BLOCK);

    struct timeval now;
    gettimeofday(&now, NULL);

    int kept = 0;
    for (int i = 0; i < CURRENT_JOBS_COUNT; i++)
    {
        pid_t pid = waitpid(CURRENT_JOBS[i].pid, NULL, WNOHANG);

        // waitpid 0 means still running, anything else means the process is gone
        if (pid == 0)
        {
            CURRENT_JOBS[kept++] = CURRENT_JOBS[i];
        }
        else if (pid > 0)
        {
            double seconds = (now.tv_sec - CURRENT_JOBS[i].start.tv_sec) + (now.tv_usec - CURRENT_JOBS[i].start.tv_usec) / MICROSECONDS_PER_SECOND;
            printf(KBLUE "\n[%d] %s- done in %.3f seconds\n" KRESET,
                   CURRENT_JOBS[i].pid, CURRENT_JOBS[i].command, seconds);
            fflush(stdout);
        }
        else
        {
            perror("Waitpid failed.");
        }
    }

    CURRENT_JOBS_COUNT = kept;
    sigchld_mask(SIG_UNBLOCK);
}

/**
 * @brief Init the SIGCHLD handler, called once when the shell starts
 * @return Return -1 if the init failed, 0 otherwise.
 */
int init_sigchld(void)
{
    struct sigaction action;
    action.sa_handler = sigchld_handler;
    sigemptyset(&action.sa_mask);
    action.sa_flags = SA_RESTART; // Calls interrupted by the signal (read in readline, waitpid) restart instead of failing
    if (sigaction(SIGCHLD, &action, NULL) < 0)
    {
        perror("sigaction");
        return -1;
    }

    return 0;
}