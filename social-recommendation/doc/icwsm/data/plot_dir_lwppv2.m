load_constants;

% init
legend_content = strvcat('Link','Wall Post','Photo','Video','All');
subm = 2;
subn = 3;
fontsize = 10;

% do plots
figure(1);
load likes_data.txt;

% Row 1: INCOMING
do_plot('k', 'INCOMING', 'TAGS INTER', likes_data, legend_content, subm, subn, 1, 1, fontsize, ...
    [get_index(AVG,IN,A,POST_TAGS); get_index(AVG,IN,A,PHOTO_TAGS); get_index(AVG,IN,A,VIDEO_TAGS); get_index(AVG,IN,A,ALL_TAGS)]);
do_plot('k', '', 'COMMENTS INTER', likes_data, legend_content, subm, subn, 1, 2, fontsize, ...
    [get_index(AVG,IN,A,LINK_COMMENTS); get_index(AVG,IN,A,POST_COMMENTS); get_index(AVG,IN,A,PHOTO_COMMENTS); get_index(AVG,IN,A,VIDEO_COMMENTS); get_index(AVG,IN,A,ALL_COMMENTS)]);
do_plot('k', '', 'LIKES INTER', likes_data, legend_content, subm, subn, 1, 3, fontsize, ...
    [get_index(AVG,IN,A,LINK_LIKES); get_index(AVG,IN,A,POST_LIKES); get_index(AVG,IN,A,PHOTO_LIKES); get_index(AVG,IN,A,VIDEO_LIKES); get_index(AVG,IN,A,ALL_LIKES)]);

% Row 2: OUTGOING
do_plot('k', 'OUTGOING', '', likes_data, legend_content, subm, subn, 2, 1, fontsize, ...
    [-1; get_index(AVG,OUT,A,POST_TAGS); get_index(AVG,OUT,A,PHOTO_TAGS); get_index(AVG,OUT,A,VIDEO_TAGS); get_index(AVG,OUT,A,ALL_TAGS)]);
do_plot('k', '', '', likes_data, legend_content, subm, subn, 2, 2, fontsize, ...
    [get_index(AVG,OUT,A,LINK_COMMENTS); get_index(AVG,OUT,A,POST_COMMENTS); get_index(AVG,OUT,A,PHOTO_COMMENTS); get_index(AVG,OUT,A,VIDEO_COMMENTS); get_index(AVG,OUT,A,ALL_COMMENTS)]);
do_plot('k', '', '', likes_data, legend_content, subm, subn, 2, 3, fontsize, ...
    [get_index(AVG,OUT,A,LINK_LIKES); get_index(AVG,OUT,A,POST_LIKES); get_index(AVG,OUT,A,PHOTO_LIKES); get_index(AVG,OUT,A,VIDEO_LIKES); get_index(AVG,OUT,A,ALL_LIKES)]);
