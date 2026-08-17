<?php
use Admin\AdminAuth;
use Admin\Flash;

AdminAuth::logout();
session_start();
Flash::info('You have been signed out.');
redirect(url('login'));
