#ifndef SIGNAL_CHILD_H
#define SIGNAL_CHILD_H

#define MICROSECONDS_PER_SECOND 1000000.0

void sigchld_mask(int how);
int init_sigchld(void);

#endif
