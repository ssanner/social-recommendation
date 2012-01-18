function [index] = get_index(avg, dir, ltype, itype)

if (itype == 22)
    disp('Check data to make sure it supports the new column ALL_INTER');
    index = -1;
    return;
end

% do_plot also changes (offset for stddev)
% index = (avg - 1).*330 + (dir - 1).*110 + (ltype - 1).*22 + itype;
index = (avg - 1).*315 + (dir - 1).*105 + (ltype - 1).*21 + itype