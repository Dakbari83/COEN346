#ifndef IO_REDIRECTIONS_H
#define IO_REDIRECTIONS_H

#include "parser.h"

#define NEW_FILE_PERMISSIONS 0644

int open_redirections_files(struct cmdline *l, int *in_fd, int *out_fd);
void close_redirections_files(int in_fd, int out_fd);
void redirect_to_files(int in_fd, int out_fd);

#endif
