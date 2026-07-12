-- CreateTable
CREATE TABLE "stored_images" (
    "id" text PRIMARY KEY,
    "key" text NOT NULL UNIQUE,
    "content_type" text NOT NULL,
    "data" text NOT NULL,
    "created_at" timestamp NOT NULL DEFAULT now()
);
