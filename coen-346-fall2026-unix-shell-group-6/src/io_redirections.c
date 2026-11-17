#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/fcntl.h>
#include <unistd.h>

#include "io_redirections.h"
#include "parser.h"

/**
 * @brief Open the redirection files used by the cmdline.
 * @param l Pointer to the cmdline
 * @param in_fd Output variable, receive the file descriptor of the in redirection or a default value of STDIN_FILENO if no in used.
 * @param out_fd Output variable, receive the file descriptor of the out redirection or a default value of STDOUT_FILENO if no in used.
 * @return -1 if an error occured.
 */
int open_redirections_files(struct cmdline *l, int *in_fd, int *out_fd)
{
    *in_fd = STDIN_FILENO;
    *out_fd = STDOUT_FILENO;

    if (l->in != 0)
    {
        *in_fd = open(l->in, O_RDONLY);

        if ((*in_fd) < 0)
        {
            perror("Failed to open the in file.");
            return -1;
        }
    }

    if (l->out != 0)
    {
        *out_fd = open(l->out, O_WRONLY | O_CREAT, NEW_FILE_PERMISSIONS);

        if ((*out_fd) < 0)
        {
            perror("Failed to open the out file.");
            return -1;
        }

        // EINVAL: devices such as /dev/null cannot be truncated, and have nothing to erase
        if (ftruncate(*out_fd, 0) < 0 && errno != EINVAL)
        {
            perror("Failed to truncate the out file.");
            close_redirections_files(*in_fd, *out_fd);
            return -1;
        }
    }

    return 0;
}

/**
 * @brief Safely close the redirection files, verifying that they are not currently used.
 * @param in_fd In file descriptor
 * @param out_fd Out file descriptor
 */
void close_redirections_files(int in_fd, int out_fd)
{
    if (in_fd != STDIN_FILENO)
        close(in_fd);
    if (out_fd != STDOUT_FILENO)
        close(out_fd);
}

/**
 * @brief Set the in and out files as redirections.
 * @param in_fd in file descriptor.
 * @param out_fd out file descriptor.
 */
void redirect_to_files(int in_fd, int out_fd)
{
    if (in_fd != STDIN_FILENO && dup2(in_fd, STDIN_FILENO) < 0)
    {
        perror("Failed to set the in redirection file.");
        exit(EXIT_FAILURE);
    }

    if (out_fd != STDOUT_FILENO && dup2(out_fd, STDOUT_FILENO) < 0)
    {
        perror("Failed to set the out redirection file.");
        exit(EXIT_FAILURE);
    }
}