#! /usr/bin/python

import pylab as plt 
from pylab import *
from collections import defaultdict

results = [line.strip().split(",") for line in open("results.txt").readlines()]		# read results file
resultsDictionary = defaultdict(list)

running = ""
for line in results:
  if "Running" in ''.join(line):
    running = ''.join(line).split(" ")[1]						# hashmap key
  if "Accuracy" in ''.join(line):
    resultsDictionary.setdefault(running,[]).append(''.join(line).split(" ")[2])	# accuracy values for key

fig1 = plt.figure(figsize = (10,15))
plt.subplots_adjust(hspace=1.0)

print resultsDictionary.items()

plotCount = 1
for key in resultsDictionary:
  count = 1
  p1 = plt.subplot(10,1,plotCount)
  plotCount+=1										# feature count
  for item in resultsDictionary[key]:
    #print count , item , key
    plt.plot(item, count, marker='o')
    plt.ylim((0,10))
    plt.xlim((0.0,1.0))
    plt.title("Thresholding for feature size using " + key)
    plt.xlabel("Accuracy")
    plt.ylabel("Feature Size")
    plt.grid(True)
    count+=1

'''
for file, name, type in plots:
  data = [line.strip().split(",") for line in open(file).readlines()]
  plt.plot([row[2] for row in data],[row[1] for row in data], type, marker='o', label=name)  
  for label, x, y in [(row[0] , row[2], row[1]) for row in data]:
   plt.annotate(
       label, xy = (x, y), xytext = (0, 12), textcoords = 'offset points', ha = 'center', va = 'center',
       bbox = dict(boxstyle = 'round,pad=0.2', fc = 'yellow', alpha = 0.5)) 
'''
#plt.savefig('plot.png')
plt.show();