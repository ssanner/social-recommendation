#! /usr/bin/python

import pylab as plt 

#plots = [("nb.txt","Naive Bayes", "b-"),("svm.txt","SVM", "r-"),("lr.txt","Regression", "g-")]
plots = [("nb.txt","Naive Bayes", "b-"),("lr.txt","Regression", "g-")]

for file, name, type in plots:
  data = [line.strip().split(",") for line in open(file).readlines()]
  plt.plot([row[2] for row in data],[row[1] for row in data], type, marker='o', label=name)  
  for label, x, y in [(row[0] , row[2], row[1]) for row in data]:
   plt.annotate(
       label, xy = (x, y), xytext = (0, 12), textcoords = 'offset points', ha = 'center', va = 'center',
       bbox = dict(boxstyle = 'round,pad=0.2', fc = 'yellow', alpha = 0.5)) 
  
plt.title("ROC Plot for 0.1 < Threshold < 0.9")
plt.xlabel("False positive rate")
plt.ylabel("Correct class identification rate")
plt.legend()
plt.grid(True)
plt.savefig('plot.png')
plt.show();
