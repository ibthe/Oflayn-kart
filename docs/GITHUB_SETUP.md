# Создание репозитория GitHub

Целевое имя репозитория: `GeorgiaOffline`.

При публикации рекомендуемые начальные настройки:

- создать пустой репозиторий без автоматически сгенерированного README (он уже есть в проекте);
- выбрать `main` как default branch;
- до выбора лицензии кода и решения о публичности предпочесть private repository;
- после первого push проверить вкладку Actions и получить APK из артефактов workflow.

Если GitHub CLI установлен и пользователь авторизован:

```bash
git init -b main
git add .
git commit -m "Initial Georgia Offline Android scaffold"
gh repo create GeorgiaOffline --private --source=. --remote=origin --push
```

Если репозиторий создан через веб-интерфейс GitHub:

```bash
git init -b main
git add .
git commit -m "Initial Georgia Offline Android scaffold"
git remote add origin https://github.com/USERNAME/GeorgiaOffline.git
git push -u origin main
```

Замените `USERNAME` на собственный логин GitHub. Не добавляйте signing keystore,
пароли, токены, приватные пользовательские данные или исходные коммерческие карты.
