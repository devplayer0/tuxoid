#ifndef DROIDTOP_H
#define DROIDTOP_H

#define SHM_PATH "/dev/shm/framebuffer"

int init(int rw, int rh, int w, int h);
void update();
void close();

bool grab(int fd);
bool ungrab(int fd);
int open_file(const char *path);
int close_file(int fd);
bool has_rel_axis(int fd, short axis);
bool has_key(int fdm, short key);
bool is_mouse(int fd);
bool is_keyboard(int fd);

#endif
