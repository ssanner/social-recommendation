#!/bin/bash 
if [ $# -ne 4 ]; then
    echo "Usage: 'foldGenerator.sh inputfile no_of_folds percentage_split outputfolder' "
fi

for ((  i = 1 ;  i <= $2;  i++  ))
do
    shuf $1 > temp
    split -l $(expr $(cat temp | wc -l) \* $3 / 100) temp
    if [ ! -d "$4" ]; then
        echo "Creating folder: $4"
        mkdir $4
    fi
    mv xaa "$4/$1.train.$i"
    mv xab "$4/$1.test.$i"
    rm temp
done
