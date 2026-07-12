-- CreateEnum
CREATE TYPE "DriverStatus" AS ENUM ('pending_documents', 'under_review', 'approved', 'rejected');

-- AlterTable
ALTER TABLE "users"
    ADD COLUMN "birth_date" TIMESTAMP(3),
    ADD COLUMN "criminal_record_url" TEXT,
    ADD COLUMN "dni" TEXT,
    ADD COLUMN "dni_photo_url" TEXT,
    ADD COLUMN "driver_status" "DriverStatus",
    ADD COLUMN "license_category" TEXT,
    ADD COLUMN "license_number" TEXT,
    ADD COLUMN "license_photo_url" TEXT;

-- CreateIndex
CREATE UNIQUE INDEX "users_dni_key" ON "users"("dni");

-- CreateIndex
CREATE UNIQUE INDEX "users_license_number_key" ON "users"("license_number");

-- AlterTable
ALTER TABLE "vehicles"
    ADD COLUMN "cedula_url" TEXT,
    ADD COLUMN "doors" INTEGER NOT NULL DEFAULT 4,
    ADD COLUMN "has_ac" BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN "has_seatbelts" BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN "insurance_expires_at" TIMESTAMP(3),
    ADD COLUMN "insurance_policy" TEXT,
    ADD COLUMN "insurance_url" TEXT,
    ADD COLUMN "vtv_expires_at" TIMESTAMP(3),
    ADD COLUMN "vtv_url" TEXT;
