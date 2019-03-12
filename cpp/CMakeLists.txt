cmake_minimum_required(VERSION 3.7)
project(planwise)

include(FindBoost)
set(Boost_USE_STATIC_LIBS ON)
find_package(Boost COMPONENTS timer program_options filesystem)
if(NOT Boost_FOUND)
  message(FATAL_ERROR "Boost not found")
endif()

find_program(GDAL_CONFIG gdal-config)
if(NOT GDAL_CONFIG)
  message(FATAL_ERROR "GDAL not found")
endif()

exec_program(${GDAL_CONFIG} ARGS --cflags OUTPUT_VARIABLE GDAL_CFLAGS)
exec_program(${GDAL_CONFIG} ARGS --libs OUTPUT_VARIABLE GDAL_LIBS)

option(BENCHMARK "add timing benchmarks" OFF)

if(BENCHMARK)
  add_definitions(-DBENCHMARK)
endif()

add_compile_options(${GDAL_CFLAGS})
include_directories(${Boost_INCLUDE_DIRS})

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -std=c++11")

add_executable(aggregate-population aggregate-population.cpp)
target_link_libraries(aggregate-population ${GDAL_LIBS} ${Boost_LIBRARIES})

add_executable(walking-coverage walking-coverage.cpp)
target_link_libraries(walking-coverage ${GDAL_LIBS} ${Boost_LIBRARIES})
