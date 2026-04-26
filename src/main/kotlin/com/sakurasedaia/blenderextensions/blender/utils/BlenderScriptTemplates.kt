package com.sakurasedaia.blenderextensions.blender.utils

object BlenderScriptTemplates {
    fun getReloadScript(port: Int, isDebug: Boolean = false): String {
        val debugSetup = if (isDebug) {
            """
                try:
                    import debugpy
                    # Use 127.0.0.1 for maximum compatibility with PyCharm's default listener
                    debugpy.listen(('127.0.0.1', 5678))
                    print("Debugpy listening on 127.0.0.1:5678 (DAP)")
                except Exception as e:
                    print(f"Failed to start debugpy: {e}")
            """.trimIndent()
        } else ""

        val enhancedLogging = """
            print(f"--- Blender Python Environment ---")
            print(f"Python Version: {sys.version}")
            print(f"Executable: {sys.executable}")
            print(f"Path: {sys.path}")
            print(f"----------------------------------")
        """.trimIndent()

        return """
            import json
            import sys
            import time
            import socket
            import traceback
            import bpy

            $enhancedLogging
            $debugSetup

            def listen_for_reload():
                
                s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                max_retries = 5
                retry_count = 0
                connected = False
                
                while retry_count < max_retries:
                    try:
                        s.connect(('127.0.0.1', $port))
                        connected = True
                        break
                    except Exception as e:
                        retry_count += 1
                        print(f"Connection attempt {retry_count} failed: {e}. Retrying in 1s...")
                        time.sleep(1)
                
                if not connected:
                    print(f"Failed to connect to IntelliJ after {max_retries} attempts.")
                    return

                try:
                    # Send ready message
                    s.sendall(json.dumps({"type": "ready"}).encode() + b"\n")
                    print(f"Connected to IntelliJ for extension reloading on port $port")
                    while True:
                        data = s.recv(1024)
                        if not data:
                            break
                        try:
                            message = json.loads(data.decode().strip())
                            if message.get('type') == 'reload':
                                extension_name = message.get('name')
                                print(f"Received reload command for: {extension_name}")
                                
                                def do_reload():
                                    try:
                                        module_name = f"bl_ext.blender_pycharm.{extension_name}"
                                        
                                        # 1. Disable if enabled
                                        if module_name in bpy.context.preferences.addons:
                                            bpy.ops.preferences.addon_disable(module=module_name)
                                        
                                        # 2. Refresh repositories to pick up file changes
                                        if hasattr(bpy.ops.extensions, 'repo_refresh_all'):
                                            bpy.ops.extensions.repo_refresh_all()
                                        
                                        # 3. Purge from sys.modules to force re-import
                                        for m in list(sys.modules.keys()):
                                            if m == module_name or m.startswith(module_name + "."):
                                                del sys.modules[m]
                                        
                                        # 4. Re-enable
                                        bpy.ops.preferences.addon_enable(module=module_name)
                                        print(f"Successfully reloaded extension: {module_name}")
                                        
                                    except Exception as e:
                                        print(f"Error during reload of {extension_name}: {e}")
                                        traceback.print_exc()
                                    return None # Don't repeat the timer
                                
                                # Use timer to run on main thread
                                import bpy
                                if hasattr(bpy.app, 'timers'):
                                    bpy.app.timers.register(do_reload)
                                else:
                                    do_reload()
                        except Exception as e:
                            print(f"Error parsing reload message: {e}")
                except Exception as e:
                    print(f"Error in listen_for_reload: {e}")
                finally:
                    s.close()

            import threading
            t = threading.Thread(target=listen_for_reload)
            t.daemon = True
            t.start()
        """.trimIndent()
    }

    fun getRepoSetupScript(repoName: String, repoPath: String): String {
        return """
            def ensure_extension_repo_exists(repo_name, repo_path):
                import bpy
                import os
                import traceback
                if bpy.app.version < (4, 2, 0):
                    return
                if not repo_path or not os.path.exists(repo_path):
                    return

                repo_path = os.path.normpath(repo_path)
                
                # Check if repo exists
                existing_repo = None
                for repo in bpy.context.preferences.extensions.repos:
                    if getattr(repo, 'module', None) == repo_name:
                        existing_repo = repo
                        break
                
                if existing_repo:
                    try:
                        existing_repo.enabled = True
                    except:
                        pass
                    current_path = getattr(existing_repo, 'directory', getattr(existing_repo, 'path', None))
                    if current_path and os.path.normpath(current_path) == repo_path:
                        print(f"Extension repo '{repo_name}' is already correctly configured.")
                        return
                    
                    print(f"Repo '{repo_name}' points to different path: {current_path}. Re-creating...")
                    try:
                        # Use the API to remove it
                        bpy.context.preferences.extensions.repos.remove(existing_repo)
                    except Exception as e:
                        print(f"Failed to remove existing repo: {e}")
                        # Attempt direct update as fallback
                        try:
                            if hasattr(existing_repo, 'directory'):
                                existing_repo.directory = repo_path
                                return
                            elif hasattr(existing_repo, 'path'):
                                existing_repo.path = repo_path
                                return
                        except Exception as e2:
                            print(f"Fallback update also failed: {e2}")

                # Add the repo
                try:
                    if hasattr(bpy.ops.preferences, 'extension_repo_add'):
                        # Blender 5.0+
                        bpy.ops.preferences.extension_repo_add(
                            name=repo_name,
                            type='LOCAL',
                            custom_directory=repo_path,
                            use_custom_directory=True
                        )
                        print(f"Added extensions repository (5.0+): {repo_name} -> {repo_path}")
                    elif hasattr(bpy.ops.extensions, 'repo_add'):
                        # Blender 4.2+
                        bpy.ops.extensions.repo_add(
                            name=repo_name,
                            type='LOCAL',
                            directory=repo_path
                        )
                        print(f"Added extensions repository (4.2+): {repo_name} -> {repo_path}")
                    else:
                        new_repo = bpy.context.preferences.extensions.repos.new(name=repo_name, module=repo_name)
                        try:
                            if hasattr(new_repo, 'directory'):
                                new_repo.directory = repo_path
                            elif hasattr(new_repo, 'path'):
                                new_repo.path = repo_path
                            new_repo.enabled = True
                        except Exception as e:
                            print(f"Failed to set path on new repo: {e}")
                        print(f"Created extensions repository (Manual): {repo_name} -> {repo_path}")
                except Exception as e:
                    print(f"Failed to create extensions repository: {e}")
                    traceback.print_exc()

            ensure_extension_repo_exists("$repoName", "$repoPath")
        """.trimIndent()
    }

    fun getAutoEnableScript(extensionName: String): String {
        return """
            def ensure_extension_enabled(extension_name):
                if not extension_name:
                    return
                
                import bpy
                module_name = f"bl_ext.blender_pycharm.{extension_name}"
                if module_name not in bpy.context.preferences.addons:
                    print(f"Automatically enabling extension: {module_name}")
                    try:
                        if hasattr(bpy.ops.extensions, 'repo_refresh_all'):
                            bpy.ops.extensions.repo_refresh_all()
                        bpy.ops.preferences.addon_enable(module=module_name)
                    except Exception as e:
                        print(f"Failed to auto-enable {module_name}: {e}")
                return None

            if hasattr(bpy.app, 'timers'):
                bpy.app.timers.register(lambda: ensure_extension_enabled("$extensionName"), first_interval=1.0)
            else:
                ensure_extension_enabled("$extensionName")
        """.trimIndent()
    }
}
