#include <stdio.h>
#include <unistd.h>

int main(void) {
    printf("sleeptest: sleeping for 77 seconds\n");
    sleep(77);
    printf("sleeptest: done\n");
    return 0;
}