CREATE DATABASE IF NOT EXISTS vspicy DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS vspicy_nacos DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS vspicy_video DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS vspicy_content DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS vspicy_audit DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS vspicy_statistics DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'vspicy'@'%' IDENTIFIED BY 'change_me';
ALTER USER 'vspicy'@'%' IDENTIFIED BY 'change_me';

GRANT ALL PRIVILEGES ON vspicy.* TO 'vspicy'@'%';
GRANT ALL PRIVILEGES ON vspicy_nacos.* TO 'vspicy'@'%';
GRANT ALL PRIVILEGES ON vspicy_video.* TO 'vspicy'@'%';
GRANT ALL PRIVILEGES ON vspicy_content.* TO 'vspicy'@'%';
GRANT ALL PRIVILEGES ON vspicy_audit.* TO 'vspicy'@'%';
GRANT ALL PRIVILEGES ON vspicy_statistics.* TO 'vspicy'@'%';

FLUSH PRIVILEGES;
