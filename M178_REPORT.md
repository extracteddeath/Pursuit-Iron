# M178 real-browser PWA update lifecycle certification

App **3.228.0**, build **784**, Pursuit Engine **0.63.2**, store schema **13**.

M176 protected interrupted-workout restoration with deterministic Node/source tests. M178 closes the remaining PWA-update validation gap by exercising the actual service-worker lifecycle in Chrome.

## Browser-certified sequence

1. Launch the real app from localhost and wait for the production worker to control it.
2. Preserve a local training-storage sentinel.
3. Change the served worker to model a newly deployed release and force an update check.
4. Assert the new worker remains `waiting` while the existing app continues running.
5. Assert the app surfaces **Update ready — Restart**.
6. Trigger Restart and require a real `controllerchange`-driven navigation.
7. Assert the new release cache is active, the old production cache has been removed, local training storage survived, and the app completed its first React commit after reload.

## Result

The current update implementation passed without a behavioral patch. The risk was missing integration coverage, not a demonstrated runtime defect. M178 therefore makes this a permanent CI contract instead of rewriting working service-worker logic.

True Android launcher/process-kill behavior still requires a physical-device certification pass because headless Chrome cannot reproduce the OS killing an installed PWA process.
