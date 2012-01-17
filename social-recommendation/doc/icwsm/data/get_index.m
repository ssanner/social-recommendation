function [index] = get_index(avg, dir, ltype, itype)

index = (avg - 1).*330 + (dir - 1).*110 + (ltype - 1).*22 + itype;