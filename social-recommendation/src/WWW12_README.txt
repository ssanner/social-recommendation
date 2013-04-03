We implemented a numbers of recommendation algorithms during the course of the research. In the first trial (Section 5.1), the following algorithms were used:

1. k-Nearest Neighbor (org.nicta.lr.recommender.NNRecommender)
2. Support Vector Machines (org.nicta.lr.recommender.SVMRecommender)
3. Matchbox (org.nicta.lr.recommender.FeatureRecommender)
4. Social Matchbox (org.nicta.lr.recommender.SocialRecommender)

For the second trial (Section 5.2), the following algorithms were used:

1. Social Matchbox (org.nicta.lr.recommender.SocialRecommender)
2. Spectral Matchbox (org.nicta.lr.recommender.SocialRecommender)
3. Social Hybrid (org.nicta.lr.recommender.HybridRecommender)
4. Spectral Copreference (org.nicta.lr.recommender.CopreferenceRecommender)

The matrix factorization based recommenders all have the abstract class org.nicta.lr.recommender.MFRecommender in their class hierarchy. The individual regularizers and objectives to be minimized are in org.nicta.lr.component. 

The objectives and components that are described in Sections 5.1 and 5.2 are:
1. OBJpmc (org.nicta.lr.component.Objective)
2. OBJru (org.nicta.lr.component.L2Regularizer)
3. OBJrv (org.nicta.lr.component.L2Regularizer
4. OBJrs (org.nicta.lr.component.SocialRegularizer)
5. OBJrss (org.nicta.lr.component.SocialSpectralRegularizer)
6. OBJcp (org.nicta.lr.component.SpectralCopreferenceRegularizer)