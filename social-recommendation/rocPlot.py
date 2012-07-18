#! /usr/bin/python

import pylab as plt
from pylab import *
from collections import defaultdict

results = [line.strip().split(",") for line in open("results.txt").readlines()]		# read results file
resultsDictionary = defaultdict(list)

running = ""
for line in results:
  if "Running" in ''.join(line):
    running = ''.join(line).split(" ")[1]											# hashmap key
  if "Accuracy" in ''.join(line):
    resultsDictionary.setdefault(running,[]).append(''.join(line).split(" ")[2])	# accuracy values for key

print resultsDictionary.items()
print "Generating " , len(resultsDictionary) , " graphs"
 
count = 0
for key in resultsDictionary:
	plt.figure(count)
	plt.plot(range(0,10), resultsDictionary[key],'ro-')
	plt.ylim((0.0,1.0))
	plt.xlim((0,10))
	plt.title("Thresholding for feature size using " + key)
	plt.xlabel("Feature Size")
	plt.ylabel("Accuracy")
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