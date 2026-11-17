#ifndef ARGS_H
#define ARGS_H

#define EXPANSION_CHARACTERS "*?[~$"

void free_args(char **args);
char **expand_args(char **args);

#endif
