package com.sakurasedaia.blenderextensions.blender

import com.intellij.openapi.components.Service
import com.intellij.openapi.application.PathManager
import java.nio.file.Files
import java.nio.file.Path

@Service
class BlenderScriptGenerator {

    fun generateStartupScriptContent(port: Int, extensionName: String?): String {
        return """
            import bpy
            import socket
            import json
            import traceback
            import time
            import sys
            
            def listen_for_reload():
                import json
                import sys
                import time
                
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

    fun createStartupScript(port: Int, repoDir: Path?, extensionName: String?): Path {
        val repoPath = repoDir?.toAbsolutePath()?.toString()?.replace("\\", "\\\\") ?: ""
        val extName = extensionName ?: ""
        val scriptContent = """
            import bpy
            import socket
            import threading
            import os
            import traceback

            def ensure_extension_repo_exists(repo_name, repo_path):
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

            def listen_for_reload():
                import json
                import sys
                import time
                
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

            ensure_extension_repo_exists("blender_pycharm", r"$repoPath")
            if hasattr(bpy.app, 'timers'):
                bpy.app.timers.register(lambda: ensure_extension_enabled("$extName"), first_interval=1.0)
            else:
                ensure_extension_enabled("$extName")
            threading.Thread(target=listen_for_reload, daemon=True).start()
        """.trimIndent()
        
        val scratchPath = Path.of(PathManager.getConfigPath(), "scratches")
        val scratchDir = if (Files.exists(scratchPath)) {
            scratchPath
        } else {
            Path.of("/home/sakura/.config/JetBrains/IntelliJIdea2025.3/scratches/")
        }
        
        if (!Files.exists(scratchDir)) {
            Files.createDirectories(scratchDir)
        }
        
        val tempFile = Files.createTempFile(scratchDir, "blender_start", ".py")
        Files.writeString(tempFile, scriptContent)
        return tempFile
    }

    fun cleanupStartupScript(scriptPath: Path?) {
        if (scriptPath == null) return
        try {
            Files.deleteIfExists(scriptPath)
        } catch (_: Exception) {
            // Best effort cleanup: script file may already be gone or still locked by the OS.
        }
    }

    companion object {
        fun getInstance(): BlenderScriptGenerator = com.intellij.openapi.application.ApplicationManager.getApplication().getService(BlenderScriptGenerator::class.java)
    }
}
