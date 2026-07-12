-- DropIndex
DROP INDEX IF EXISTS "users_license_number_key";

-- AlterTable
ALTER TABLE "users" DROP COLUMN IF EXISTS "license_number";
