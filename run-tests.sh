#!/usr/bin/env bash
set -e

echo "=== Running Backend Tests ==="
cd backend && ./mvnw test -q
echo "Backend tests passed."
echo ""

echo "=== Running Frontend Tests ==="
cd ../frontend && npm test
echo "Frontend tests passed."
echo ""

echo "=== All tests passed ==="
