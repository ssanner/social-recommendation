load_constants;

% init
legend_content = strvcat('Real','Virtual','Friends');
subm = 1;
subn = 2;
fontsize = 10;

% do plots
figure(1);
load likes_data.txt;

% Row 1: INCOMING
do_plot('k', 'REAL VS VIRTUAL INTER', 'INCOMING', likes_data, legend_content, subm, subn, 1, 1, fontsize, ...
    [get_index(AVG,IN,A,REAL); get_index(AVG,IN,A,VIRTUAL); get_index(AVG,IN,A,FRIENDS)]);
do_plot('k', '', 'OUTGOING', likes_data, legend_content, subm, subn, 1, 2, fontsize, ...
    [get_index(AVG,OUT,A,REAL); get_index(AVG,OUT,A,VIRTUAL); get_index(AVG,OUT,A,FRIENDS)]);

