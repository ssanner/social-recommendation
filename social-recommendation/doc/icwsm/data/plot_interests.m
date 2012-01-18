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
figure(2);
n = [1,2,4,8,16,32,64,128];
%n = 1:8;
fontsize = 14;
k = 1;

%hold off;
%errorbar(n,GR(1:8,2),GR(1:8,12),'k.:');
%hold on;
%errorbar(n,GL(1:8,2),GL(1:8,12),'r.-');
%errorbar(n,MOV(1:8,2),MOV(1:8,12),'g.-');
%errorbar(n,MUS(1:8,2),MUS(1:8,12),'b.--');
%errorbar(n,TEL(1:8,2),TEL(1:8,12),'m.:');

hold off;
plot(n,GR(1:8,k),'k*:');
hold on;
plot(n,GL(1:8,k),'ro-');
plot(n,MOV(1:8,k),'g^-');
plot(n,MUS(1:8,k),'bv--');
plot(n,TEL(1:8,k),'ms:');


set(gca,'xscale','log');
xlabel('n: Groups Shared with Maximum n Friends','FontSize',fontsize);
ylabel(['P(likes | k=',num2str(k),', n)'],'FontSize',fontsize);
legend('Groups','Page Likes', 'Movies', 'Music', 'Television');
axis tight;

