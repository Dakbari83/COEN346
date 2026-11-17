#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wordexp.h>

#include "expansion_args.h"
#include "utils.h"

/**
 * @brief Adds a copy of a word at the end of a null-terminated array of words
 * @param words the array, reallocated to make room
 * @param count number of words already in the array
 * @param word the word to copy
 * @return the reallocated array
 */
static char **append_word(char **words, int *count, const char *word)
{
    // Room for the new word and the final null pointer
    words = xrealloc(words, ((*count) + 2) * sizeof(char *));
    words[*count] = strdup(word);
    if (words[*count] == NULL)
        memory_error();
    words[++(*count)] = NULL;
    return words;
}

/**
 * @brief Frees an array of words made by expand_args
 * @param args the array to free
 */
void free_args(char **args)
{
    for (int i = 0; args[i] != NULL; i++)
        free(args[i]);
    free(args);
}

/**
 * @brief Expands wildcards (* ? []), ~ and environment variables ($HOME) in a command's words
 * @param args the command and its arguments, as given by the parser
 * @return a new array of words ending with a null pointer, to be released with free_args.
 * It can be empty, e.g. when the only word is an unset $VARIABLE.
 */
char **expand_args(char **args)
{
    int count = 0;
    char **words = xmalloc(sizeof(char *));
    words[0] = NULL;

    for (int i = 0; args[i] != NULL; i++)
    {
        wordexp_t expansion;
        // WRDE_NOCMD refuses $(command): expanding a word must never run a program
        if (strpbrk(args[i], EXPANSION_CHARACTERS) != NULL && wordexp(args[i], &expansion, WRDE_NOCMD) == 0)
        {
            for (size_t j = 0; j < expansion.we_wordc; j++)
            {
                words = append_word(words, &count, expansion.we_wordv[j]);
            }
            wordfree(&expansion);
        }
        else
        {
            words = append_word(words, &count, args[i]);
        }
    }
    return words;
}
