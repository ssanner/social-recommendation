#! /usr/bin/python

import pylab as plt 

plots = [("datak1000.arff_NaiveBayes_ROC.data","Naive Bayes", "b-"),("rocplot.data","SVM", "r-"),("rocplot2.data","Regression", "g-")]

for file, name, type in plots:
  data = [line.strip().split(" ") for line in open(file).readlines()]
  plt.plot([row[1] for row in data],[row[2] for row in data], type, marker='o', label=name)  

plt.title("ROC Plot for 0.1 < Threshold < 0.9")
plt.xlabel("False positive rate")
plt.ylabel("Correct class identification rate")
plt.legend()
plt.grid(True)
plt.savefig('plot.png')
#plt.show();

