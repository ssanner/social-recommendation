function [index] = get_index(avg, dir, ltype, itype)

index = (avg - 1).*315 + (dir - 1).*105 + (ltype - 1).*21 + itype;