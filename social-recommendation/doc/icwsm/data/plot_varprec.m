function [] = plot_varprec(name, sz, plot_title, legend_pos, subfig)

fontsize = 14;

data = [];
varname = strtrim(['varprec_',name,'_',num2str(sz)]);
disp(['load data\',varname,'.txt']);
eval(['load data\',varname,'.txt']);
str = ['data=',varname,';'];
eval(str);

figure(1);

if (subfig > 0)
    subplot(1,3,subfig);
end

hold off
plot(data(:,4),data(:,2)./100,'ro-');
hold on
plot(data(:,7),data(:,5)./100,'bs--');
max_time = max(max(data(:,2)),max(data(:,4)))./100;
xlabel('True Approximation Error','FontSize',fontsize);
if (subfig == 1)
    legend('APRICODD (ADD)','MADCAP (AADD)','FontSize',fontsize,'Location',legend_pos);
    ylabel('Execution Time (s)','FontSize',fontsize);
end
title(plot_title,'FontSize',fontsize);
%axis tight;
%axis([0,60,0,max_time.*1.1]);

print(gcf, '-depsc', [varname,'_time.eps']);
fixPSlinestyle([varname,'_time.eps'],[varname,'_time_fix.eps']);

figure(2);
if (subfig > 0)
    subplot(1,3,subfig);
end

hold off
plot(data(:,4),data(:,3),'ro-');
hold on
plot(data(:,7),data(:,6),'bs--');
max_space = max(max(data(:,3)),max(data(:,5)));
xlabel('True Approximation Error','FontSize',fontsize);
if (subfig == 1)
    legend('APRICODD (ADD)','MADCAP (AADD)','FontSize',fontsize,'Location',legend_pos);
    ylabel('Space (# Nodes)','FontSize',fontsize);
end
%title(plot_title,'FontSize',fontsize);
%axis tight;
%axis([0,60,0,max_space.*1.1]);

print(gcf, '-depsc', [varname,'_space.eps']);
fixPSlinestyle([varname,'_space.eps'],[varname,'_space_fix.eps']);

