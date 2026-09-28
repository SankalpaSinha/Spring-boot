-- ---------------------------------------------------------------------------
-- PointsCore :: reference data
--
-- Tiers, promotions and the reward catalogue for a demo coffee-chain programme.
-- This is reference data rather than test fixtures: the app is not usable
-- without at least a base earn rule and a default tier, so it ships as a
-- migration instead of sitting in a test helper.
-- ---------------------------------------------------------------------------

INSERT INTO tiers (code, name, min_points_12m, earn_multiplier, sort_order) VALUES
    ('SILVER',   'Silver',   0,     1.00, 1),
    ('GOLD',     'Gold',     5000,  1.25, 2),
    ('PLATINUM', 'Platinum', 20000, 1.50, 3);


-- The base rule converts money into points. Exactly one BASE rule should be
-- active at a time; the promotions below multiply whatever it produces.
INSERT INTO earn_rules (code, name, rule_type, conditions, multiplier, priority, active) VALUES
    ('BASE_EARN',
     '1 point per 100 spent',
     'BASE',
     '{"pointsPerUnit": 100}'::jsonb,
     1.00,
     0,
     TRUE);


-- Promotions. Each contributes a bonus when its condition matches; see the
-- stacking rule documented in EarnRuleEngine.
INSERT INTO earn_rules (code, name, rule_type, conditions, multiplier, priority, active) VALUES
    ('WEEKEND_2X',
     'Double points at the weekend',
     'DAY_OF_WEEK',
     '{"days": ["SATURDAY", "SUNDAY"]}'::jsonb,
     2.00,
     10,
     TRUE),

    ('COFFEE_3X',
     'Triple points on coffee',
     'CATEGORY',
     '{"categories": ["COFFEE"]}'::jsonb,
     3.00,
     20,
     TRUE),

    ('BIG_BASKET_1_5X',
     'One and a half points on baskets over 2000',
     'MIN_AMOUNT',
     '{"minAmount": 2000}'::jsonb,
     1.50,
     30,
     TRUE);


INSERT INTO rewards (code, name, description, cost_points, stock) VALUES
    ('FREE_LATTE',    'Free latte',        'Any regular latte, on us.',               250,  500),
    ('COFFEE_BEANS',  '250g coffee beans', 'A bag of single-origin beans.',           1200, 120),
    ('BRANDED_MUG',   'Branded mug',       'Ceramic mug. Deliberately low stock, to demonstrate oversell protection.', 800, 10);
