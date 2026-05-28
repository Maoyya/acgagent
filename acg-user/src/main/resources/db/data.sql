-- ============================================================
-- 种子数据 — 角色、权限、用户
-- 执行顺序：先执行 schema.sql 建表，再执行本文件插入数据
-- ============================================================

USE acg_agent;

-- -----------------------------------------------------------
-- 1. 角色（3 条）
-- -----------------------------------------------------------
INSERT INTO sys_role (id, name, code, sort, status, remark) VALUES
(1, '管理员',   'admin',    1, 1, '系统管理员，拥有全部权限'),
(2, '普通用户', 'user',     2, 1, '普通注册用户，基础业务权限'),
(3, 'VIP用户',  'user_vip', 3, 1, 'VIP 用户，在普通用户基础上可能享有高级功能');

-- -----------------------------------------------------------
-- 2. 权限（12 条，树形结构）
--
-- 结构概览：
--   id=1  系统管理 (MENU) ── id=5 用户管理 (MENU)
--                             id=6 角色管理 (MENU)
--                             id=7 权限管理 (MENU)
--   id=2  业务功能 (MENU) ── id=8 素材库 (MENU)
--                             id=9 创作工坊 (MENU)
--   id=3  Agent管理 (MENU) ── id=10 Agent管理 (MENU)
--   id=4  个人中心 (MENU) ── id=11 个人信息 (MENU)
--                             id=12 修改密码 (MENU)
-- -----------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, name, code, type, path, icon, sort, status) VALUES
-- 一级菜单（type=1 菜单）
(1,  0, '系统管理',   'system',          1, NULL,           'Setting',       1, 1),
(2,  0, '业务功能',   'business',        1, NULL,           'Grid',          2, 1),
(3,  0, 'Agent管理',  'agent',           1, NULL,           'Monitor',       3, 1),
(4,  0, '个人中心',   'profile',         1, NULL,           'UserFilled',    4, 1),
-- 二级菜单（type=1 菜单，带路径）
(5,  1, '用户管理',   'system:user',     1, '/system/users',       'User',  1, 1),
(6,  1, '角色管理',   'system:role',     1, '/system/roles',       'Lock',  2, 1),
(7,  1, '权限管理',   'system:perm',     1, '/system/permissions', 'Key',   3, 1),
(8,  2, '素材库',     'business:assets', 1, '/assets',             'FolderOpened', 1, 1),
(9,  2, '创作工坊',   'business:workshop',1, '/workshop/new',      'Film',   2, 1),
(10, 3, 'Agent管理',  'agent:manage',    1, '/agents',             'Monitor', 1, 1),
(11, 4, '个人信息',   'profile:info',    1, '/profile',            'User',   1, 1),
(12, 4, '修改密码',   'profile:password',1, '/profile',            'Lock',   2, 1);

-- -----------------------------------------------------------
-- 3. 角色-权限映射
--    admin      → 全部 12 条权限
--    user       → 业务功能 + 个人中心（6 条：id 2,8,9,4,11,12）
--    user_vip   → 业务功能 + 个人中心（6 条：id 2,8,9,4,11,12）
-- -----------------------------------------------------------
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
-- admin：全部权限
(1, 1),  (1, 2),  (1, 3),  (1, 4),
(1, 5),  (1, 6),  (1, 7),  (1, 8),
(1, 9),  (1, 10), (1, 11), (1, 12),
-- user：业务功能 + 个人中心
(2, 2),  (2, 8),  (2, 9),
(2, 4),  (2, 11), (2, 12),
-- user_vip：业务功能 + 个人中心
(3, 2),  (3, 8),  (3, 9),
(3, 4),  (3, 11), (3, 12);

-- -----------------------------------------------------------
-- 4. 管理员用户
--    用户名: admin
--    密码:   admin123（BCrypt 加密）
-- -----------------------------------------------------------
INSERT INTO sys_user (id, username, password, nickname, status) VALUES
(1, 'admin', '$2a$10$u9zaG9JZgm0aK3HLDpexn.NOCH76ShDlVqi6Rh320bVEfAlVx4bDK', '系统管理员', 1);

-- -----------------------------------------------------------
-- 5. 管理员-角色绑定
-- -----------------------------------------------------------
INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 1);
