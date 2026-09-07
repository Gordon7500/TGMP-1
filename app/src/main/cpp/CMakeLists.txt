cmake_minimum_required(VERSION 3.22.1)
project(tdbridge)

# The CI workflow copies the TDLib-built libtdjson.so into jniLibs before Gradle runs,
# so it exists at this relative path by the time CMake configures.
add_library(tdjson SHARED IMPORTED)
set_target_properties(tdjson PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libtdjson.so
)

add_library(tdbridge SHARED tdbridge.c)
target_link_libraries(tdbridge tdjson)
