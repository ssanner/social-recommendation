load_constants;

% init
legend_content = strvcat('Link Inter','Post Inter','Photo Inter','Video Inter');
subm = 4;
subn = 3;
fontsize = 10;

% do plots
figure(1);
load likes_data.txt;

% Row 1: LINK LIKES
do_plot('k', 'LINK LIKES', 'TAGS INTER', likes_data, legend_content, subm, subn, 1, 1, fontsize, ...
    [get_index(AVG,OUT,L,POST_TAGS); get_index(AVG,OUT,L,PHOTO_TAGS); get_index(AVG,OUT,L,VIDEO_TAGS)]);
do_plot('k', '', 'COMMENTS INTER', likes_data, legend_content, subm, subn, 1, 2, fontsize, ...
    [get_index(AVG,OUT,L,LINK_COMMENTS); get_index(AVG,OUT,L,POST_COMMENTS); get_index(AVG,OUT,L,PHOTO_COMMENTS); get_index(AVG,OUT,L,VIDEO_COMMENTS)]);
do_plot('k', '', 'LIKES INTER', likes_data, legend_content, subm, subn, 1, 3, fontsize, ...
    [get_index(AVG,OUT,L,LINK_LIKES); get_index(AVG,OUT,L,POST_LIKES); get_index(AVG,OUT,L,PHOTO_LIKES); get_index(AVG,OUT,L,VIDEO_LIKES)]);

% Row 2: POST LIKES
do_plot('k', 'LINK LIKES', 'TAGS INTER', likes_data, legend_content, subm, subn, 2, 1, fontsize, ...
    [get_index(AVG,OUT,P,POST_TAGS); get_index(AVG,OUT,P,PHOTO_TAGS); get_index(AVG,OUT,P,VIDEO_TAGS)]);
do_plot('k', '', 'COMMENTS INTER', likes_data, legend_content, subm, subn, 2, 2, fontsize, ...
    [get_index(AVG,OUT,P,LINK_COMMENTS); get_index(AVG,OUT,P,POST_COMMENTS); get_index(AVG,OUT,P,PHOTO_COMMENTS); get_index(AVG,OUT,P,VIDEO_COMMENTS)]);
do_plot('k', '', 'LIKES INTER', likes_data, legend_content, subm, subn, 2, 3, fontsize, ...
    [get_index(AVG,OUT,P,LINK_LIKES); get_index(AVG,OUT,P,POST_LIKES); get_index(AVG,OUT,P,PHOTO_LIKES); get_index(AVG,OUT,P,VIDEO_LIKES)]);

% Row 3: PHOTO LIKES
do_plot('k', 'LINK LIKES', 'TAGS INTER', likes_data, legend_content, subm, subn, 3, 1, fontsize, ...
    [get_index(AVG,OUT,PH,POST_TAGS); get_index(AVG,OUT,PH,PHOTO_TAGS); get_index(AVG,OUT,PH,VIDEO_TAGS)]);
do_plot('k', '', 'COMMENTS INTER', likes_data, legend_content, subm, subn, 3, 2, fontsize, ...
    [get_index(AVG,OUT,PH,LINK_COMMENTS); get_index(AVG,OUT,PH,POST_COMMENTS); get_index(AVG,OUT,PH,PHOTO_COMMENTS); get_index(AVG,OUT,PH,VIDEO_COMMENTS)]);
do_plot('k', '', 'LIKES INTER', likes_data, legend_content, subm, subn, 3, 3, fontsize, ...
    [get_index(AVG,OUT,PH,LINK_LIKES); get_index(AVG,OUT,PH,POST_LIKES); get_index(AVG,OUT,PH,PHOTO_LIKES); get_index(AVG,OUT,PH,VIDEO_LIKES)]);

% Row 4: VIDEO LIKES
do_plot('k', 'LINK LIKES', 'TAGS INTER', likes_data, legend_content, subm, subn, 4, 1, fontsize, ...
    [get_index(AVG,OUT,V,POST_TAGS); get_index(AVG,OUT,V,PHOTO_TAGS); get_index(AVG,OUT,V,VIDEO_TAGS)]);
do_plot('k', '', 'COMMENTS INTER', likes_data, legend_content, subm, subn, 4, 2, fontsize, ...
    [get_index(AVG,OUT,V,LINK_COMMENTS); get_index(AVG,OUT,V,POST_COMMENTS); get_index(AVG,OUT,V,PHOTO_COMMENTS); get_index(AVG,OUT,V,VIDEO_COMMENTS)]);
do_plot('k', '', 'LIKES INTER', likes_data, legend_content, subm, subn, 4, 3, fontsize, ...
    [get_index(AVG,OUT,V,LINK_LIKES); get_index(AVG,OUT,V,POST_LIKES); get_index(AVG,OUT,V,PHOTO_LIKES); get_index(AVG,OUT,V,VIDEO_LIKES)]);
