<?php
declare(strict_types=1);

namespace QuickTap\Core;

/** Strict input validation. Every controller runs request data through this. */
final class Validator
{
    /** @var array<string,string> */
    private array $errors = [];
    /** @var array<string,mixed> */
    private array $clean = [];

    public function __construct(private array $data) {}

    public function required(string $field, int $max = 255): self
    {
        $v = trim((string) ($this->data[$field] ?? ''));
        if ($v === '') {
            $this->errors[$field] = "$field is required";
        } elseif (mb_strlen($v) > $max) {
            $this->errors[$field] = "$field must be at most $max characters";
        } else {
            $this->clean[$field] = $v;
        }
        return $this;
    }

    public function optional(string $field, int $max = 255, ?string $default = null): self
    {
        $v = trim((string) ($this->data[$field] ?? ''));
        if ($v === '') {
            $this->clean[$field] = $default;
        } elseif (mb_strlen($v) > $max) {
            $this->errors[$field] = "$field must be at most $max characters";
        } else {
            $this->clean[$field] = $v;
        }
        return $this;
    }

    public function email(string $field, bool $required = true): self
    {
        $v = trim((string) ($this->data[$field] ?? ''));
        if ($v === '') {
            if ($required) $this->errors[$field] = "$field is required";
            else $this->clean[$field] = null;
            return $this;
        }
        if (!filter_var($v, FILTER_VALIDATE_EMAIL)) {
            $this->errors[$field] = "$field must be a valid email";
        } else {
            $this->clean[$field] = $v;
        }
        return $this;
    }

    public function number(string $field, float $min = -1e12, float $max = 1e12, float $default = 0): self
    {
        $raw = $this->data[$field] ?? null;
        if ($raw === null || $raw === '') {
            $this->clean[$field] = $default;
            return $this;
        }
        if (!is_numeric($raw)) {
            $this->errors[$field] = "$field must be numeric";
            return $this;
        }
        $n = (float) $raw;
        if ($n < $min || $n > $max) {
            $this->errors[$field] = "$field out of range";
        } else {
            $this->clean[$field] = $n;
        }
        return $this;
    }

    public function integer(string $field, int $min = 0, int $max = PHP_INT_MAX, int $default = 0): self
    {
        $raw = $this->data[$field] ?? null;
        if ($raw === null || $raw === '') { $this->clean[$field] = $default; return $this; }
        if (filter_var($raw, FILTER_VALIDATE_INT) === false) {
            $this->errors[$field] = "$field must be an integer";
            return $this;
        }
        $n = (int) $raw;
        if ($n < $min || $n > $max) $this->errors[$field] = "$field out of range";
        else $this->clean[$field] = $n;
        return $this;
    }

    public function boolean(string $field, bool $default = false): self
    {
        $raw = $this->data[$field] ?? null;
        $this->clean[$field] = $raw === null ? $default : (bool) filter_var($raw, FILTER_VALIDATE_BOOLEAN);
        return $this;
    }

    public function inList(string $field, array $allowed, ?string $default = null): self
    {
        $v = (string) ($this->data[$field] ?? '');
        if ($v === '' && $default !== null) { $this->clean[$field] = $default; return $this; }
        if (!in_array($v, $allowed, true)) {
            $this->errors[$field] = "$field must be one of: " . implode(', ', $allowed);
        } else {
            $this->clean[$field] = $v;
        }
        return $this;
    }

    public function uuid(string $field, bool $required = true): self
    {
        $v = trim((string) ($this->data[$field] ?? ''));
        if ($v === '') {
            if ($required) $this->errors[$field] = "$field is required";
            else $this->clean[$field] = null;
            return $this;
        }
        if (!preg_match('/^[0-9a-fA-F-]{8,36}$/', $v)) {
            $this->errors[$field] = "$field must be a valid uuid";
        } else {
            $this->clean[$field] = $v;
        }
        return $this;
    }

    public function hexColor(string $field, string $default = '#0E9F6E'): self
    {
        $v = trim((string) ($this->data[$field] ?? ''));
        if ($v === '') { $this->clean[$field] = $default; return $this; }
        if (!preg_match('/^#[0-9A-Fa-f]{6}$/', $v)) {
            $this->errors[$field] = "$field must be a #RRGGBB colour";
        } else {
            $this->clean[$field] = strtoupper($v);
        }
        return $this;
    }

    public function arrayOf(string $field, bool $required = true): self
    {
        $v = $this->data[$field] ?? null;
        if (!is_array($v)) {
            if ($required) $this->errors[$field] = "$field must be an array";
            else $this->clean[$field] = [];
            return $this;
        }
        $this->clean[$field] = $v;
        return $this;
    }

    public function fails(): bool
    {
        return $this->errors !== [];
    }

    /** Sends a 422 and exits when validation failed. */
    public function validOrFail(): array
    {
        if ($this->fails()) {
            Response::error('Validation failed', 422, $this->errors, 'VALIDATION_ERROR');
        }
        return $this->clean;
    }
}
