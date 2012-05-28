#! /usr/bin/python

import pylab as plt 

#plots = [("nb.txt","Naive Bayes", "b-"),("svm.txt","SVM", "r-"),("lr.txt","Regression", "g-")]
plots = [("nb.txt","Naive Bayes", "b-")]

for file, name, type in plots:
  data = [line.strip().split(",") for line in open(file).readlines()]
  plt.plot([row[2] for row in data],[row[1] for row in data], type, marker='o', label=name)  

plt.title("ROC Plot for 0.1 < Threshold < 0.9")
plt.xlabel("False positive rate")
plt.ylabel("Correct class identification rate")
plt.legend()
plt.grid(True)
plt.savefig('plot.png')
plt.show();

