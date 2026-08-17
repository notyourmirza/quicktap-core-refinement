<?php
declare(strict_types=1);

/** Global view/controller helpers for the Super Admin panel. */

function e(mixed $value): string
{
    return htmlspecialchars((string) ($value ?? ''), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function redirect(string $url): never
{
    header('Location: ' . $url);
    exit;
}

function is_post(): bool
{
    return ($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST';
}

function post(string $key, string $default = ''): string
{
    $v = $_POST[$key] ?? $default;
    return is_string($v) ? trim($v) : $default;
}

function post_int(string $key, int $default = 0): int
{
    return isset($_POST[$key]) && is_numeric($_POST[$key]) ? (int) $_POST[$key] : $default;
}

function post_float(string $key, float $default = 0.0): float
{
    return isset($_POST[$key]) && is_numeric($_POST[$key]) ? (float) $_POST[$key] : $default;
}

function post_bool(string $key): int
{
    return !empty($_POST[$key]) ? 1 : 0;
}

function post_null(string $key): ?string
{
    $v = post($key);
    return $v === '' ? null : $v;
}

function query(string $key, string $default = ''): string
{
    $v = $_GET[$key] ?? $default;
    return is_string($v) ? trim($v) : $default;
}

function query_int(string $key, int $default = 0): int
{
    return isset($_GET[$key]) && is_numeric($_GET[$key]) ? (int) $_GET[$key] : $default;
}

/** Keeps a value inside a whitelist — used for sort columns that cannot be bound. */
function pick(string $value, array $allowed, string $fallback): string
{
    return in_array($value, $allowed, true) ? $value : $fallback;
}

function url(string $page, array $params = []): string
{
    return 'index.php?' . http_build_query(['p' => $page] + $params);
}

function money(mixed $amount, string $currency = 'Rs'): string
{
    return $currency . ' ' . number_format((float) $amount, 2);
}

function nice_date(?string $value, string $format = 'd M Y, H:i'): string
{
    if ($value === null || $value === '' || str_starts_with($value, '0000')) {
        return '—';
    }
    return date($format, strtotime($value));
}

function status_badge(string $status): string
{
    $map = [
        'active'    => 'success',
        'completed' => 'success',
        'pending'   => 'warning',
        'suspended' => 'danger',
        'blocked'   => 'danger',
        'expired'   => 'secondary',
        'reset'     => 'info',
        'refunded'  => 'warning',
        'void'      => 'secondary',
    ];
    $tone = $map[$status] ?? 'secondary';
    return '<span class="badge text-bg-' . $tone . ' text-capitalize">' . e($status) . '</span>';
}

function valid_hex(string $color, string $fallback): string
{
    return preg_match('/^#[0-9a-fA-F]{6}$/', $color) === 1 ? strtoupper($color) : $fallback;
}

function uuid4(): string
{
    $d = random_bytes(16);
    $d[6] = chr((ord($d[6]) & 0x0f) | 0x40);
    $d[8] = chr((ord($d[8]) & 0x3f) | 0x80);
    return vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex($d), 4));
}

/** Simple pagination math. @return array{page:int,per:int,offset:int,pages:int} */
function paginate(int $total, int $per = 20): array
{
    $per   = max(5, min(100, $per));
    $pages = max(1, (int) ceil($total / $per));
    $page  = max(1, min($pages, query_int('page', 1)));
    return ['page' => $page, 'per' => $per, 'offset' => ($page - 1) * $per, 'pages' => $pages];
}

function pager(array $p, string $page, array $params = []): string
{
    if ($p['pages'] < 2) {
        return '';
    }
    $out = '<nav><ul class="pagination pagination-sm mb-0">';
    for ($i = 1; $i <= $p['pages']; $i++) {
        if ($p['pages'] > 9 && $i > 3 && $i < $p['pages'] - 2 && abs($i - $p['page']) > 1) {
            if ($i === 4) {
                $out .= '<li class="page-item disabled"><span class="page-link">…</span></li>';
            }
            continue;
        }
        $active = $i === $p['page'] ? ' active' : '';
        $out .= '<li class="page-item' . $active . '"><a class="page-link" href="'
              . e(url($page, $params + ['page' => $i])) . '">' . $i . '</a></li>';
    }
    return $out . '</ul></nav>';
}

/**
 * Schema guards — the panel must never fatal because a migration has not been
 * applied yet. Pages call these and render a friendly notice instead.
 */
function table_exists(string $table): bool
{
    static $cache = [];
    if (isset($cache[$table])) {
        return $cache[$table];
    }
    try {
        \QuickTap\Core\Database::run('SELECT 1 FROM `' . $table . '` LIMIT 1');
        return $cache[$table] = true;
    } catch (\Throwable) {
        return $cache[$table] = false;
    }
}

/** @return string[] column names of a table (empty when the table is missing) */
function table_columns(string $table): array
{
    static $cache = [];
    if (isset($cache[$table])) {
        return $cache[$table];
    }
    try {
        $rows = \QuickTap\Core\Database::all('SHOW COLUMNS FROM `' . $table . '`');
        return $cache[$table] = array_column($rows, 'Field');
    } catch (\Throwable) {
        return $cache[$table] = [];
    }
}

function has_column(string $table, string $column): bool
{
    return in_array($column, table_columns($table), true);
}

function missing_table_notice(string $table, string $migration): string
{
    return '<div class="alert alert-warning">The <code>' . e($table) . '</code> table does not exist yet. '
         . 'Run <code>server/sql/schema.sql</code> on your database, then reload this page.</div>';
}

// ---------------------------------------------------------------------------
// Subscription helpers — the admin panel is the only authority that assigns,
// activates, renews or expires a shop's plan. Devices only read the result.
// ---------------------------------------------------------------------------

/** Duration presets offered next to the plan picker (months => label). */
function plan_durations(): array
{
    return [
        1  => '1 month',
        3  => '3 months',
        6  => '6 months',
        12 => '12 months',
        24 => '24 months',
        0  => 'Lifetime / no expiry',
    ];
}

/**
 * Resolves the subscription window from the plan, the chosen duration and any
 * manual dates the admin typed. Manual dates always win.
 *
 * @return array{0:?string,1:?string} [starts_at, ends_at] as Y-m-d or null
 */
function plan_window(?string $startsAt, ?string $endsAt, int $months, ?string $billingCycle = null): array
{
    $start = $startsAt !== null && $startsAt !== '' ? $startsAt : date('Y-m-d');

    if ($endsAt !== null && $endsAt !== '') {
        return [$start, $endsAt];       // manual expiry wins
    }
    if ($months < 0) {
        $months = 0;
    }
    if ($months === 0) {
        // No duration chosen: fall back to the plan's own billing cycle.
        $months = match ($billingCycle) {
            'monthly' => 1,
            'yearly'  => 12,
            default   => 0,             // lifetime / unknown => no expiry
        };
    }
    if ($months === 0) {
        return [$start, null];
    }
    return [$start, date('Y-m-d', strtotime($start . ' +' . $months . ' months'))];
}

/** Days left on a subscription, or null when it never expires. */
function plan_days_left(?string $endsAt): ?int
{
    if ($endsAt === null || $endsAt === '') {
        return null;
    }
    $end = strtotime($endsAt . ' 23:59:59');
    return $end === false ? null : (int) max(0, ceil(($end - time()) / 86400));
}

/** True when the stored expiry date is in the past. */
function plan_is_expired(?string $endsAt): bool
{
    if ($endsAt === null || $endsAt === '') {
        return false;
    }
    $end = strtotime($endsAt . ' 23:59:59');
    return $end !== false && $end < time();
}

/**
 * Shop status implied by the plan assignment. "Active" only sticks while the
 * expiry date is still ahead — anything else falls back to expired/pending.
 */
function plan_effective_status(?int $planId, string $requestedStatus, ?string $endsAt): string
{
    if (!$planId) {
        return $requestedStatus === 'active' ? 'pending' : $requestedStatus;
    }
    if ($requestedStatus === 'active' && plan_is_expired($endsAt)) {
        return 'expired';
    }
    return $requestedStatus;
}
