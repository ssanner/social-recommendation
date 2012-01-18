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
do_plot('k', 'LINK LIKES', 'TAGS INTER', likes_data, legend_content, subm, subn, 1, 1, fontsize, ...
    [get_index(AVG,IN,L,ALL_TAGS); get_index(AVG,OUT,L,ALL_TAGS); get_index(AVG,IN,L,FRIENDS)]);
do_plot('k', '', 'COMMENTS INTER', likes_data, legend_content, subm, subn, 1, 2, fontsize, ...
    [get_index(AVG,IN,L,ALL_COMMENTS); get_index(AVG,OUT,L,ALL_COMMENTS); get_index(AVG,IN,L,FRIENDS)]);
do_plot('k', '', 'LIKES INTER', likes_data, legend_content, subm, subn, 1, 3, fontsize, ...
    [get_index(AVG,IN,L,ALL_LIKES); get_index(AVG,OUT,L,ALL_LIKES); get_index(AVG,IN,L,FRIENDS)]);

% Row 2: POST
do_plot('k', 'POST LIKES', '', likes_data, legend_content, subm, subn, 2, 1, fontsize, ...
    [get_index(AVG,IN,P,ALL_TAGS); get_index(AVG,OUT,P,ALL_TAGS); get_index(AVG,IN,P,FRIENDS)]);
do_plot('k', '', '', likes_data, legend_content, subm, subn, 2, 2, fontsize, ...
    [get_index(AVG,IN,P,ALL_COMMENTS); get_index(AVG,OUT,P,ALL_COMMENTS); get_index(AVG,IN,P,FRIENDS)]);
do_plot('k', '', '', likes_data, legend_content, subm, subn, 2, 3, fontsize, ...
    [get_index(AVG,IN,P,ALL_LIKES); get_index(AVG,OUT,P,ALL_LIKES); get_index(AVG,IN,P,FRIENDS)]);

% Row 3: PHOTO
do_plot('k', 'PHOTO LIKES', '', likes_data, legend_content, subm, subn, 3, 1, fontsize, ...
    [get_index(AVG,IN,PH,ALL_TAGS); get_index(AVG,OUT,PH,ALL_TAGS); get_index(AVG,IN,PH,FRIENDS)]);
do_plot('k', '', '', likes_data, legend_content, subm, subn, 3, 2, fontsize, ...
    [get_index(AVG,IN,PH,ALL_COMMENTS); get_index(AVG,OUT,PH,ALL_COMMENTS); get_index(AVG,IN,PH,FRIENDS)]);
do_plot('k', '', '', likes_data, legend_content, subm, subn, 3, 3, fontsize, ...
    [get_index(AVG,IN,PH,ALL_LIKES); get_index(AVG,OUT,PH,ALL_LIKES); get_index(AVG,IN,PH,FRIENDS)]);

% Row 4: VIDEO
do_plot('k', 'VIDEO LIKES', '', likes_data, legend_content, subm, subn, 4, 1, fontsize, ...
    [get_index(AVG,IN,V,ALL_TAGS); get_index(AVG,OUT,V,ALL_TAGS); get_index(AVG,IN,V,FRIENDS)]);
do_plot('k', '', '', likes_data, legend_content, subm, subn, 4, 2, fontsize, ...
    [get_index(AVG,IN,V,ALL_COMMENTS); get_index(AVG,OUT,V,ALL_COMMENTS); get_index(AVG,IN,V,FRIENDS)]);
do_plot('k', '', '', likes_data, legend_content, subm, subn, 4, 3, fontsize, ...
    [get_index(AVG,IN,V,ALL_LIKES); get_index(AVG,OUT,V,ALL_LIKES); get_index(AVG,IN,V,FRIENDS)]);
