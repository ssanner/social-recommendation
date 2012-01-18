function [] = save(name)

print(gcf, '-depsc', [name,'.eps']);
fixPSlinestyle([name,'.eps'], [name,'_fix.eps']);