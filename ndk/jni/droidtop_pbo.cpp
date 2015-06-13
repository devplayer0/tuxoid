#include <stdio.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <sys/shm.h>
#include <linux/input.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <stdlib.h>

#include <GLES3/gl3.h>

#include "droidtop.h"
#include "util.h"

int width;
int height;

int size;
int shmfd;
void *data;
GLuint pboIds[2];
GLuint textureId;

int sp_image;

float mtrxProjection[16];
float mtrxView[16];
float mtrxProjectionAndView[16];

float vertices[] = {
		0.0f, 1.0f, 0.0f, 	// 0.0f,         (float)height, 0.0f
		0.0f, 0.0f, 0.0f,	// 0.0f,         0.0f,          0.0f
		1.0f, 0.0f, 0.0f,	// (float)width, 0.0f,          0.0f
		1.0f, 1.0f, 0.0f    // (float)width, (float)height, 0.0f
};
short indices[] = { 0, 1, 2, 0, 2, 3 };
float uvs[] =  {
		0.0f, 0.0f,
		0.0f, 1.0f,
		1.0f, 1.0f,
		1.0f, 0.0f
};

int setup_shm(int width, int height) {
    size = width * height * 4;

    shmfd = open(SHM_PATH, O_RDONLY | O_CLOEXEC | O_NOFOLLOW, S_IRWXU | S_IRWXG);

    if (shmfd < 0) {
        log("failed to open shared memory");
        log(strerror(errno));
				return -1;
    }
    ftruncate(shmfd, size);

    data = mmap(NULL, size, PROT_READ, MAP_SHARED, shmfd, 0);
    if (data == NULL) {
        log("failed to map memory");
				close(shmfd);
				return -2;
    }

	return 0;
}
void setup_texture(int width, int height) {
	vertices[1] = (float)height;
	vertices[6] = (float)width;
	vertices[9] = (float)width;
	vertices[10] = (float)height;

    // Generate Texture
	glGenTextures(1, &textureId);

	// Bind texture to texturename
	glActiveTexture(GL_TEXTURE0);
	glBindTexture(GL_TEXTURE_2D, textureId);

	// Set filtering
	glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
	glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

	// Set wrapping mode
	glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
	glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

	glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
	glBindTexture(GL_TEXTURE_2D, 0);

	log("using pbos...");
	glGenBuffers(2, pboIds);
    glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboIds[0]);
    glBufferData(GL_PIXEL_UNPACK_BUFFER, width * height * 4, 0, GL_STREAM_DRAW);
    glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboIds[1]);
    glBufferData(GL_PIXEL_UNPACK_BUFFER, width * height * 4, 0, GL_STREAM_DRAW);
    glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
}
void setup_viewport(int width, int height) {
	glViewport(0, 0, width, height);

	// Setup our screen width and height for normal sprite translation.
	orthoM(mtrxProjection, 0, 0, width, 0, height, 0, 50);
	// Set the camera position (View matrix)
	setLookAtM(mtrxView, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0);
	// Calculate the projection and view transformation
	multiplyMM(mtrxProjectionAndView, mtrxProjection, mtrxView);
}
int init(int w, int h) {
    int ret = setup_shm(w, h);
	if (ret != 0) {
		return ret;
	}
	setup_texture(w, h);
	setup_viewport(w, h);

    // Set the clear color to black
    glClearColor(0.0f, 0.0f, 0.0f, 1);

    // Create the shaders
    int vertex_shader = load_shader(GL_VERTEX_SHADER, vs_image);
    int fragment_shader = load_shader(GL_FRAGMENT_SHADER, fs_image);

    sp_image = glCreateProgram();              // create empty OpenGL ES Program
    glAttachShader(sp_image, vertex_shader);   // add the vertex shader to program
    glAttachShader(sp_image, fragment_shader); // add the fragment shader to program
    glLinkProgram(sp_image);                   // creates OpenGL ES program executables

    // Set our shader program
    glUseProgram(sp_image);

	width = w;
	height = h;

	return 0; // do we need more error handling code??
}

void update_pbos() {
	static int index = 0;
    int nextIndex = 0;
    //index = (index + 1) % 2;
    //nextIndex = (index + 1) % 2;

    glBindTexture(GL_TEXTURE_2D, textureId);
    glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboIds[index]);

    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, 0);

    glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboIds[nextIndex]);

    glBufferData(GL_PIXEL_UNPACK_BUFFER, width * height * 4, 0, GL_STREAM_DRAW);
    GLubyte* ptr = (GLubyte*)glMapBufferRange(GL_PIXEL_UNPACK_BUFFER, 0, width * height * 4, GL_MAP_WRITE_BIT);
    if (ptr) {
    	memcpy(ptr, data, width * height * 4);
    	glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER);
    }
    glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
}
void update() {
    //glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, data); // send the texture data
    update_pbos();

    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    // get handle to vertex shader's vPosition member
    int positionHandle = glGetAttribLocation(sp_image, "vPosition");

    // Enable generic vertex attribute array
    glEnableVertexAttribArray(positionHandle);

    // Prepare the triangle coordinate data
    glVertexAttribPointer(positionHandle, 3, GL_FLOAT, false, 0, vertices);

    // Get handle to texture coordinates location
    int texCoordLoc = glGetAttribLocation(sp_image, "a_texCoord");

    // Enable generic vertex attribute array
    glEnableVertexAttribArray(texCoordLoc);

    // Prepare the texturecoordinates
    glVertexAttribPointer(texCoordLoc, 2, GL_FLOAT, false, 0, uvs);

    // Get handle to shape's transformation matrix
    int mtrxhandle = glGetUniformLocation(sp_image, "uMVPMatrix");

    // Apply the projection and view transformation
    glUniformMatrix4fv(mtrxhandle, 1, false, mtrxProjectionAndView);

    // Get handle to textures locations
    int samplerLoc = glGetUniformLocation(sp_image, "s_texture");

    // Set the sampler texture unit to 0, where we have saved the texture.
    glUniform1i(samplerLoc, 0);

    // Draw the triangle
    glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_SHORT, indices);

    // Disable vertex array
    glDisableVertexAttribArray(positionHandle);
    glDisableVertexAttribArray(texCoordLoc);

    glBindTexture(GL_TEXTURE_2D, 0);
}

void close() {
	glDeleteProgram(sp_image);

	munmap(data, size);
	close(shmfd);
}

bool grab(int fd) {
    return ioctl(fd, EVIOCGRAB, 1) == 0;
}
bool ungrab(int fd) {
    return ioctl(fd, EVIOCGRAB, 0) == 0;
}

int open_file(const char *path) {
    return open(path, O_RDWR);
}
int close_file(int fd) {
    return close(fd);
}

bool has_rel_axis(int fd, short axis) {
    unsigned char relBitmask[(REL_MAX + 1) / 8];
    
    ioctl(fd, EVIOCGBIT(EV_REL, sizeof(relBitmask)), relBitmask);
    
    return test_bit(axis, relBitmask);
}
bool has_key(int fd, short key) {
    unsigned char keyBitmask[(KEY_MAX + 1) / 8];
    
    ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(keyBitmask)), keyBitmask);
    
    return test_bit(key, keyBitmask);
}

bool is_mouse(int fd) {
    return has_rel_axis(fd, 0x00) && has_rel_axis(fd, 0x01) && has_key(fd, 0x110); // REL_X, REL_Y, BTN_LEFT
}
bool is_keyboard(int fd) {
    return has_key(fd, 16); // KEY_Q
}