cmake_minimum_required(VERSION 3.10)
project(planwise)

include(FindGDAL)
include(FindBoost)
find_package(Boost COMPONENTS timer)
if(NOT Boost_FOUND)
  message(FATAL_ERROR "Boost not found")
endif()

if(NOT GDAL_FOUND)
  message(FATAL_ERROR "GDAL not found")
endif()

option(BENCHMARK "add timing benchmarks" OFF)

if(BENCHMARK)
  add_definitions(-DBENCHMARK)
endif()

include_directories(${Boost_INCLUDE_DIRS})

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -std=c++11")

add_executable(aggregate-population aggregate-population.cpp)
target_link_libraries(aggregate-population ${GDAL_LIBRARY} ${Boost_LIBRARIES})

add_executable(calculate-demand calculate-demand.cpp)
target_link_libraries(calculate-demand ${GDAL_LIBRARY} ${Boost_LIBRARIES})
