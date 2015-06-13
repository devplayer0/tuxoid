#ifndef UTIL_H
#define UTIL_H

#include <string>

#define test_bit(bit, array)    (array[bit/8] & (1<<(bit%8)))

int log(const char *msg);

extern std::string vs_image;
extern std::string fs_image;

int load_shader(int type, std::string shaderCode);

float length(float x, float y, float z);
void orthoM(float *m, int mOffset, float left, float right, float bottom, float top, float near, float far);
void translateM(float *m, int mOffset, float x, float y, float z);
void setLookAtM(float *rm, int rmOffset, float eyeX, float eyeY, float eyeZ, float centerX, float centerY, float centerZ, float upX, float upY, float upZ);
void multiplyMM(float *r, const float *lhs, const float *rhs);

#endif
