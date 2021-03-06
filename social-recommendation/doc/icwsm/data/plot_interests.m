load GENERAL_LIKES_probs.txt
GL = GENERAL_LIKES_probs;
load GROUPS_probs.txt
GR = GROUPS_probs;
load MOVIES_probs.txt;
MOV = MOVIES_probs;
load MUSIC_probs.txt;
MUS = MUSIC_probs;
load TELEVISION_probs.txt;
TEL = TELEVISION_probs;
load SCHOOL_probs.txt;
SCH = SCHOOL_probs;
load WORK_probs.txt;
WK = WORK_probs;
figure(2);
n = [1,2,4,8,16,32,64,128];
%n = 1:8;
fontsize = 10;
k = 2;
t = 2;
subplot(2,2,k + 2*(t-1));

%hold off;
%errorbar(n,GR(1:8,2),GR(1:8,12),'k.:');
%hold on;
%errorbar(n,GL(1:8,2),GL(1:8,12),'r.-');
%errorbar(n,MOV(1:8,2),MOV(1:8,12),'g.-');
%errorbar(n,MUS(1:8,2),MUS(1:8,12),'b.--');
%errorbar(n,TEL(1:8,2),TEL(1:8,12),'m.:');

if (t == 1)
    hold off;
    plot(n,GR(1:8,k),'k*:');
    hold on;
    plot(n,GL(1:8,k),'ro-');
    plot(n,MOV(1:8,k),'g^-');
    plot(n,MUS(1:8,k),'bv--');
    plot(n,TEL(1:8,k),'ms:');
else
    hold off;
    plot(n,WK(1:8,k),'rs-');    
    hold on;
    plot(n,SCH(1:8,k),'b^:');
end


set(gca,'xscale','log');
if (t == 1)
    title(['k=',num2str(k)],'FontSize',fontsize);
end
xlabel('n: Groups Shared with Maximum n Friends','FontSize',fontsize);
if (k ==1)
    if (t == 1)
        ylabel('INTERESTS','FontSize',fontsize);
    else
        ylabel('HISTORY','FontSize',fontsize);
    end
end
if (k > 1) 
    if (t == 1)
        legend('Groups','Page Likes', 'Movies', 'Music', 'Television');
    else
        legend('Schoolmates', 'Co-workers');
    end
end
axis tight;

