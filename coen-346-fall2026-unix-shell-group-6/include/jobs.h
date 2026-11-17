#ifndef JOBS_H
#define JOBS_H

#include <sys/fcntl.h>
#include <sys/time.h>

#define MAX_JOBS 100
#define MAX_COMMAND_LENGTH 256

struct Job
{
    pid_t pid;
    char command[MAX_COMMAND_LENGTH];
    struct timeval start;
};

extern struct Job CURRENT_JOBS[MAX_JOBS];
extern int CURRENT_JOBS_COUNT;

void print_jobs(void);
int has_room_for_jobs(int count);
void add_job(pid_t pid, char **args);

#endif