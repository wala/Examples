import * as path from 'path';

import * as vscode from 'vscode';

import {
  ShowMessageParams,
  LanguageClient,
  LanguageClientOptions,
  ClientCapabilities,
  StaticFeature
} from 'vscode-languageclient';

export class ShowHTMLFeature implements StaticFeature {
    fillClientCapabilities(capabilities: ClientCapabilities): void {
      capabilities.experimental = { supportsShowHTML: true };
    }
    initialize(): void {
    }
}

let client: LanguageClient;

export function activate(context: vscode.ExtensionContext) {

  const serverOptions = {
      command: "java",
      args: [
        //"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,quiet=y,address=localhost:8111",
        "-jar",
        path.resolve(__dirname, '../../resources') + '/zhinu-0.0.1-SNAPSHOT-织女.jar'
      ]
  }

  // Options to control the language client
  let clientOptions: LanguageClientOptions = {
    // Register the server for plain text documents
    documentSelector: [{ scheme: 'file', language: 'python' }]
  };

  // Create the language client and start the client.
  client = new LanguageClient(
    'languageServerExample',
    'Language Server Example',
    serverOptions,
    clientOptions
  );

  client.registerFeature(new ShowHTMLFeature());

  client.onReady().then(() => {
    client.onNotification("magpie/showHTML", (msg: ShowMessageParams) => {
      // Create and show panel
      const panel = vscode.window.createWebviewPanel(
        'magpieBridge',
        'MagpieBridge',
        vscode.ViewColumn.One,
        {
                   enableScripts: true,
                   enableCommandUris: true
        }
      );
      
      // And set its HTML content
      panel.webview.html = msg.message
    });
  });

  // Start the client. This will also launch the server
  context.subscriptions.push(client.start());
}

export function deactivate(): Thenable<void> {
  if (!client) {
    return undefined;
  }
  return client.stop();
}

