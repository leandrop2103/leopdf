# PdfCraft 📄✨

<p align="center">
  <img src="assets/app-icon/hicolor/256x256/apps/ai.storyteller.pdfcraft.png" width="120" height="120" alt="PdfCraft Icon">
</p>

<p align="center">
  <b>O leitor e organizador de PDF moderno, rápido e imersivo.</b><br>
  Desenvolvido nativamente em <b>Kotlin</b> para Android e <b>Rust</b> para Desktop.<br>
  Sem anúncios · Sem rastreamento · 100% Offline e Privado
</p>

<p align="center">
  <a href="https://github.com/leandrop2103/pdfcraft/releases"><img src="https://img.shields.io/badge/Versão-0.0.10-blue.svg?style=for-the-badge" alt="Versão Atual"></a>
  <a href="LICENSE-MIT"><img src="https://img.shields.io/badge/Licença-MIT-green.svg?style=for-the-badge" alt="Licença MIT"></a>
  <img src="https://img.shields.io/badge/Plataforma-Android%20%7C%20Windows%20%7C%20Linux%20%7C%20macOS-orange.svg?style=for-the-badge" alt="Plataformas">
  <img src="https://img.shields.io/badge/Linguagens-Kotlin%20%7C%20Rust-purple.svg?style=for-the-badge" alt="Linguagens">
</p>

---

## 📱 Destaques do PdfCraft para Android

O **PdfCraft Android** é um leitor completo projetado para oferecer a melhor experiência de leitura no smartphone, trazendo recursos avançados que rivalizam e superam outros leitores tradicionais:

### 🌙 Modo Noturno Adobe (OLED Pure Black)
Inversão de luminância que transforma o fundo do PDF em **preto OLED puro (`#000000`)** e os textos escuros em **branco cristalino (`#FFFFFF`)**. Elimina a fadiga visual à noite e proporciona máxima economia de bateria em telas AMOLED. O botão de acesso rápido na barra permite alternar entre dia e noite com um único toque.

### 📖 Modo Leitura Kindle E-Ink (E-Paper)
Transforme o documento na experiência de leitura de um **Kindle Paperwhite**. Aplica uma desaturação monocromática completa com contraste expandido sobre fundo texturizado em tom de papel suave (`#F4F4F0`), reduzindo reflexos e brilhos excessivos.

### ✂️ Recorte Inteligente de Margens (Smart Crop 3mm)
Varredura matemática de pixels em tempo real que detecta o conteúdo real (tintas e textos) em cada página e apara bordas brancas vazias desnecessárias, preservando um respiro confortável de 3mm em todas as direções para maximizar a área útil da tela.

### 🔍 Zoom Adaptativo de Toque Duplo
Dois toques rápidos na tela ajustam o zoom e enquadram os parágrafos exatamente na largura do display. Suporte completo a gestos multitoque (pinch-to-zoom) e pan com inércia física natural.

### 📜 Rolagem Contínua Vertical
Leitura fluida sem cortes entre as páginas, com renderização assíncrona, paginação contínua e gerenciamento dinâmico de memória para abrir arquivos grandes instantaneamente.

### 🔊 Leitura em Voz Alta (Text-to-Speech / TTS)
Ouça seus PDFs como audiobooks! O app sintetiza e lê o conteúdo textual em voz alta com player flutuante integrado.

### ▶️ Rolagem Automática (Hands-Free)
Ative a rolagem automática com velocidade configurável de 0.5x até 3.0x para ler livros e apostilas sem precisar tocar na tela.

### ✍️ Desenhos, Anotações e Assinatura Digital
Camada de anotações com traço vetorial de tinta suave, seleção de cores (Azul, Preto e Vermelho), ferramenta de desfazer (Undo) e salvamento no documento.

### 🖼️ Mosaico de Miniaturas de Páginas
Grade visual em mosaico de 3 colunas para navegação rápida e visualização instantânea de qualquer página do documento.

### 💾 Armazenamento Local Persistente Offline (`DocumentStorage`)
Quando você abre arquivos da nuvem (como Microsoft OneDrive, Google Drive ou anexos de mensagens), o app armazena uma cópia segura e privada em armazenamento local interno. Isso previne erros de expiração de permissão e garante que você sempre consiga reabrir seus documentos recentes mesmo offline.

---

## 🖥️ Versão Desktop (Rust Workbench)

O PdfCraft também conta com uma versão de desktop multiplataforma desenvolvida em **Rust**:
- **Motor nativo e seguro:** Rápido, leve e com foco em fidelidade visual (ISO 32000-2).
- **Sem telemetria e sem contas:** Nenhum dado sai do seu computador.
- **Ferramentas de produtividade:** Leitura, reorganização de páginas, mesclagem (combine), divisão (split), anotações e proteção.
- Suporte a Windows, Linux, macOS e FreeBSD.

---

## 🚀 Instalação

### 📱 Android
1. Baixe o instalador mais recente: **[`PdfCraft-v0.0.10.apk`](https://github.com/leandrop2103/pdfcraft/releases)**.
2. No seu aparelho Android, abra o arquivo APK baixado.
3. Se solicitado, autorize a instalação de fontes desconhecidas e confirme.
4. Abra o PdfCraft e aproveite seus documentos!

### 💻 Compilação Desktop (Rust)
Requisitos: Rust 1.80+ instalado.
```bash
# Clone o repositório
git clone https://github.com/leandrop2103/pdfcraft.git
cd pdfcraft

# Execute a versão desktop
cargo run --release -p pdfcraft-gui
```

---

## 👥 Colaboradores & Autoria

O desenvolvimento deste aplicativo foi realizado em conjunto por:

| Colaborador | Função & Contribuições |
|---|---|
| **[Leandro](https://github.com/leandrop2103)** | **Idealizador, Arquiteto de Software & Engenharia de Produto**<br>Definição de funcionalidades, design de interface, testes em dispositivos reais, usabilidade, especificações dos modos de leitura e direção técnica do projeto. |
| **[Antigravity](https://github.com/leandrop2103/pdfcraft)** *(Google DeepMind)* | **Agente de Engenharia & Co-desenvolvedor de Software**<br>Implementação do leitor Android (Kotlin + Rust JNI), matrizes de cores para os modos Kindle E-Ink e Noturno Adobe, algoritmo de recorte inteligente 3mm, persistência `DocumentStorage`, controle de concorrência e mitigação de falhas. |

---

## 📄 Licença

Este projeto é software livre e de código aberto disponibilizado sob a **[Licença MIT](LICENSE-MIT)**.

```
MIT License

Copyright (c) 2026 Leandro and Contribuidores do PdfCraft

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

---

<p align="center">
  Construído com dedicação para uma leitura de PDFs verdadeiramente agradável, produtiva e sem distrações.
</p>
