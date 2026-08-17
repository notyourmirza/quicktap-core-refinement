<?php
declare(strict_types=1);

namespace QuickTap\Controllers;

use QuickTap\Core\{Auth, Database, Request, Response, Validator};

/**
 * Marketplace. The catalogue itself is published through app_settings
 * (see ConfigController::themeFor); this controller only receives the
 * "Buy now" forms filled in on the device and files them as requests
 * for the Super Admin panel.
 */
final class MarketController
{
    /** Catalogue for the signed-in shop — same list the theme payload carries. */
    /**
     * Subscription plans exactly as the Super Admin published them. The device
     * plan store renders this list, so pricing and copy change without an app
     * update. Extended marketing columns are optional (see the plans_store
     * migration) — the payload falls back gracefully when they are absent.
     */
    public function plans(Request $req): void
    {
        $ctx = Auth::requireUser($req);

        $rows = Database::all('SELECT * FROM plans WHERE is_active = 1');
        usort($rows, static function (array $a, array $b): int {
            $sa = (int) ($a['sort_order'] ?? 0);
            $sb = (int) ($b['sort_order'] ?? 0);
            return $sa === $sb ? ((float) $a['price'] <=> (float) $b['price']) : ($sa <=> $sb);
        });

        $plans = [];
        foreach ($rows as $r) {
            $features = json_decode((string) ($r['features_json'] ?? '[]'), true);
            $monthly  = (float) $r['price'];
            $yearly   = (float) ($r['price_yearly'] ?? 0);
            $plans[]  = [
                'code'         => (string) $r['code'],
                'name'         => (string) $r['name'],
                'tagline'      => (string) ($r['tagline'] ?? ''),
                'tag'          => ($r['tag'] ?? '') !== '' ? (string) $r['tag'] : null,
                'price'        => $monthly,
                'price_yearly' => $yearly > 0 ? $yearly : round($monthly * 10, 2),
                'max_devices'  => (int) $r['max_devices'],
                'max_users'    => (int) $r['max_users'],
                'max_products' => (int) $r['max_products'],
                'features'     => is_array($features) ? array_values($features) : [],
            ];
        }

        // Current subscription: when a plan is active the device only offers
        // an extension of that plan instead of the whole catalogue.
        $shop = Database::first(
            'SELECT s.plan_id, s.status, s.subscription_starts_at, s.subscription_ends_at,
                    p.code AS plan_code
               FROM shops s LEFT JOIN plans p ON p.id = s.plan_id
              WHERE s.id = :s LIMIT 1',
            ['s' => $ctx['shop_id']]
        );

        $endsAt = $shop['subscription_ends_at'] ?? null;
        $active = $shop
            && ($shop['status'] ?? '') === 'active'
            && !empty($shop['plan_code'])
            && ($endsAt === null || strtotime((string) $endsAt) >= time());

        Response::ok([
            'plans'   => $plans,
            'current' => $active ? [
                'code'      => (string) $shop['plan_code'],
                'status'    => (string) $shop['status'],
                'starts_at' => $shop['subscription_starts_at'] ?? null,
                'ends_at'   => $endsAt,
                'days_left' => $endsAt
                    ? max(0, (int) ceil((strtotime((string) $endsAt) - time()) / 86400))
                    : null,
            ] : null,
        ]);
    }

    /**
     * Hardware / accessory order form. Subscription plans are assigned by the
     * Super Admin only, so plan items are rejected here.
     */
    public function request(Request $req): void
    {
        $ctx = Auth::requireUser($req);
        if (str_starts_with((string) $req->input('item_code', ''), 'plan_')) {
            Response::error('Plans are assigned by your administrator.', 403, null, 'PLAN_PURCHASE_DISABLED');
        }
        $in  = (new Validator($req->body))
            ->required('item_code', 80)
            ->required('item_name', 160)
            ->required('contact_name', 120)
            ->required('contact_phone', 40)
            ->integer('quantity', 1, 999)
            ->optional('address', 500)
            ->optional('note', 500)
            ->validOrFail();

        $qty   = max(1, (int) $in['quantity']);
        $price = round((float) ($req->input('unit_price', 0)), 2);

        Database::run(
            'INSERT INTO market_requests
                (shop_id, user_id, item_code, item_name, quantity, unit_price, total_price,
                 contact_name, contact_phone, address, note)
             VALUES (:s,:u,:code,:name,:q,:up,:tp,:cn,:cp,:addr,:note)',
            [
                's' => $ctx['shop_id'], 'u' => $ctx['user_id'],
                'code' => $in['item_code'], 'name' => $in['item_name'],
                'q' => $qty, 'up' => $price, 'tp' => round($price * $qty, 2),
                'cn' => $in['contact_name'], 'cp' => $in['contact_phone'],
                'addr' => $in['address'], 'note' => $in['note'],
            ]
        );

        $id = Database::insertId();
        Auth::log($ctx['shop_id'], 'user', $ctx['user_id'], 'market_request', 'market_request', (string) $id);
        Response::ok(['id' => $id], 'Request sent. Our team will contact you shortly.');
    }

    /** Request history for the shop, so the device can show its own orders. */
    public function requests(Request $req): void
    {
        $ctx = Auth::requireUser($req);
        Response::ok(Database::all(
            'SELECT id, item_code, item_name, quantity, total_price, status, created_at
               FROM market_requests WHERE shop_id = :s ORDER BY id DESC LIMIT 50',
            ['s' => $ctx['shop_id']]
        ));
    }
}
