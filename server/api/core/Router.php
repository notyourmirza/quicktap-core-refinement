<?php
declare(strict_types=1);

namespace QuickTap\Core;

/** Minimal path router with {param} support. */
final class Router
{
    /** @var array<int,array{method:string,pattern:string,handler:callable}> */
    private array $routes = [];

    public function add(string $method, string $pattern, callable $handler): void
    {
        $this->routes[] = ['method' => strtoupper($method), 'pattern' => $pattern, 'handler' => $handler];
    }

    public function get(string $p, callable $h): void    { $this->add('GET', $p, $h); }
    public function post(string $p, callable $h): void   { $this->add('POST', $p, $h); }
    public function put(string $p, callable $h): void    { $this->add('PUT', $p, $h); }
    public function delete(string $p, callable $h): void { $this->add('DELETE', $p, $h); }

    public function dispatch(Request $req): void
    {
        if ($req->method === 'OPTIONS') {
            http_response_code(204);
            exit;
        }

        foreach ($this->routes as $route) {
            $regex = '#^' . preg_replace('#\{([a-zA-Z_]+)\}#', '(?P<$1>[^/]+)', $route['pattern']) . '$#';
            if (preg_match($regex, $req->path, $m)) {
                if ($route['method'] !== $req->method) {
                    continue;
                }
                $params = array_filter($m, 'is_string', ARRAY_FILTER_USE_KEY);
                ($route['handler'])($req, $params);
                return;
            }
        }
        Response::error('Endpoint not found: ' . $req->path, 404, null, 'NOT_FOUND');
    }
}
