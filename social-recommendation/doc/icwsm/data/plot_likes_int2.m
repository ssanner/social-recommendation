load_constants;

% init
legend_content = strvcat('Incoming','Outgoing','Friends');
subm = 4;
subn = 3;
fontsize = 10;

% do plots
figure(1);
load likes_data.txt;

% Row 1: LINK
do_plot('k', 'LINK INTER', 'TAG INTER', likes_data, legend_content, subm, subn, 1, 1, fontsize, ...
    [-1; -1; get_index(AVG,IN,A,FRIENDS)]); % No link tags
do_plot('k', '', 'COMMENT INTER', likes_data, legend_content, subm, subn, 1, 2, fontsize, ...
    [get_index(AVG,IN,A,LINK_COMMENTS); get_index(AVG,OUT,A,LINK_COMMENTS); get_index(AVG,IN,A,FRIENDS)]);
do_plot('k', '', 'LIKES INTER', likes_data, legend_content, subm, subn, 1, 3, fontsize, ...
    [get_index(AVG,IN,A,LINK_LIKES); get_index(AVG,OUT,A,LINK_LIKES); get_index(AVG,IN,A,FRIENDS)]);

% Row 2: POST
do_plot('k', 'POST INTER', '', likes_data, legend_content, subm, subn, 2, 1, fontsize, ...
    [get_index(AVG,IN,A,POST_TAGS); get_index(AVG,OUT,A,POST_TAGS); get_index(AVG,IN,A,FRIENDS)]);
do_plot('k', '', '', likes_data, legend_content, subm, subn, 2, 2, fontsize, ...
    [get_index(AVG,IN,A,POST_COMMENTS); get_index(AVG,OUT,A,POST_COMMENTS); get_index(AVG,IN,A,FRIENDS)]);
do_plot('k', '', '', likes_data, legend_content, subm, subn, 2, 3, fontsize, ...
    [get_index(AVG,IN,A,POST_LIKES); get_index(AVG,OUT,A,POST_LIKES); get_index(AVG,IN,A,FRIENDS)]);

% Row 3: PHOTO
do_plot('k', 'PHOTO INTER', '', likes_data, legend_content, subm, subn, 3, 1, fontsize, ...
    [get_index(AVG,IN,A,PHOTO_TAGS); get_index(AVG,OUT,A,PHOTO_TAGS); get_index(AVG,IN,A,FRIENDS)]);
do_plot('k', '', '', likes_data, legend_content, subm, subn, 3, 2, fontsize, ...
    [get_index(AVG,IN,A,PHOTO_COMMENTS); get_index(AVG,OUT,A,PHOTO_COMMENTS); get_index(AVG,IN,A,FRIENDS)]);
do_plot('k', '', '', likes_data, legend_content, subm, subn, 3, 3, fontsize, ...
    [get_index(AVG,IN,A,PHOTO_LIKES); get_index(AVG,OUT,A,PHOTO_LIKES); get_index(AVG,IN,A,FRIENDS)]);

% Row 4: VIDEO
do_plot('k', 'VIDEO INTER', '', likes_data, legend_content, subm, subn, 4, 1, fontsize, ...
    [get_index(AVG,IN,A,VIDEO_TAGS); get_index(AVG,OUT,A,VIDEO_TAGS); get_index(AVG,IN,A,FRIENDS)]);
do_plot('k', '', '', likes_data, legend_content, subm, subn, 4, 2, fontsize, ...
    [get_index(AVG,IN,A,VIDEO_COMMENTS); get_index(AVG,OUT,A,VIDEO_COMMENTS); get_index(AVG,IN,A,FRIENDS)]);
do_plot('k', '', '', likes_data, legend_content, subm, subn, 4, 3, fontsize, ...
    [get_index(AVG,IN,A,VIDEO_LIKES); get_index(AVG,OUT,A,VIDEO_LIKES); get_index(AVG,IN,A,FRIENDS)]);
