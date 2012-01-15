function [] = do_plot(xlabel_name, ylabel_name, likes_data, legend_content, subm, subn, subfigm, subfign, fontsize, data)

%fontsize = 14;
subplot(subm,subn,(subfigm-1)*subn+subfign,'replace');

% data comes in rows
num_lines = size(data,1) % rows

% plot remaining lines
if (num_lines == 3)
    line_types = { 'b.--', 'r.-', 'g.:' };
elseif (num_lines == 4)
    line_types = { 'r.-', 'b.--', 'g.-.', 'm.:' };
else
    line_types = { 'r.-', 'b.--', 'g.-.', 'k.:', 'm.:' };
end
hold off;
plotted = 0;
for j = 1:num_lines
    if plotted == 1
        hold on;
    end
    if (data(j) == -1)
        plot([1],[0],line_types{j});
    else
        errorbar([1:10],likes_data(data(j),:),likes_data(315 + data(1),:),line_types{j});
    end
    plotted = 1;
end

xlabel(xlabel_name,'FontSize',fontsize);
ylabel(ylabel_name,'FontSize',fontsize);
axis tight;
if subfigm == subm && subfign == subn
    legend(legend_content);
end

%print(gcf, '-depsc', [varname,'_error.eps']);
%fixPSlinestyle([varname,'_error.eps'],[varname,'_error_fix.eps']);


