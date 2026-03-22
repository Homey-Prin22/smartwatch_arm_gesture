import os
import json
import random
from flask_sqlalchemy import SQLAlchemy
from flask import Flask, render_template, redirect, url_for, flash, request, send_from_directory, jsonify
from flask_login import LoginManager, UserMixin, login_user, login_required, logout_user, current_user
from werkzeug.security import generate_password_hash, check_password_hash


app = Flask(__name__)
app.config['SECRET_KEY'] = 'your_secret_key'
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///users.db'
db = SQLAlchemy(app)

login_manager = LoginManager()
login_manager.init_app(app)
login_manager.login_view = 'login'


class User(UserMixin, db.Model):
    id = db.Column(db.Integer, primary_key=True)
    userid = db.Column(db.String(150), unique=True, nullable=False)


@login_manager.user_loader
def load_user(user_id):
    return db.session.get(User, int(user_id))


@app.route('/register', methods=['GET', 'POST'])
def register():
    if request.method == 'POST':
        userid = request.form['userid']

        existing_user = User.query.filter_by(userid=userid).first()
        if existing_user:
            flash('UserID già esistente')
            return redirect(url_for('register'))

        new_user = User(userid=userid)
        db.session.add(new_user)
        db.session.commit()
        flash('La registrazione è avvenuta con successo, si prega di effettuare il login')
        return redirect(url_for('login'))

    return render_template('register.html')


@app.route('/login', methods=['GET', 'POST'])
def login():
    if request.method == 'POST':
        userid = request.form['userid']
        user = User.query.filter_by(userid=userid).first()

        if user:
            login_user(user)
            app.config['user'] = user
            app.config['images'] = generate_shuffled_list(images, NUM_REPEAT, DEST_DIR, app.config['user'])
            return redirect(url_for('gesture'))
        else:
            flash('UserID non esistente')

    return render_template('login.html')


@login_required
@app.route('/gesture')
def gesture():
    if not app.config['images']:
        return "Nessuna immagine trovata", 500
    return render_template('gesture.html', image_fn=app.config['images'][app.config['current_index']][0])


@app.route('/logout')
@login_required
def logout():
    logout_user()
    return redirect(url_for('login'))


def shuffle(array):
    random.shuffle(array)
    return array


def generate_shuffled_list(elements, repeat, dest_dir, user):
    dest_dir = os.path.join(dest_dir, user.userid)
    stored_files = {os.path.splitext(file)[0] for file in os.listdir(dest_dir)} if os.path.exists(dest_dir) else set()
    print(stored_files)
    list_data = []
    for element in elements:
        for i in range(repeat):
            fn = f"{os.path.splitext(element)[0]}_gesture{i}"
            if fn not in stored_files:
                list_data.append((element, i))
    print(list_data)
    return shuffle(list_data)


@login_required
@app.route('/save', methods=['POST'])
def save():
    response = request.get_data()
    app.config['data'] = json.loads(response)
    return jsonify({'redirect': url_for('confirm')})


@app.route('/confirm', methods=['POST'])
def confirm():
    action = request.json.get('action')
    if action == 'reject':
        if 'data' in app.config:
            app.config.pop('data')
        return jsonify({"message": "Dati rigettati"}), 200

    if not 'data' in app.config:
        return jsonify({"message": "Registrare i dati con lo smartwatch"}), 200
    else:
        data = app.config['data']
        class_name = os.path.splitext(app.config['images'][app.config['current_index']][0])[0]
        rep = app.config['images'][app.config['current_index']][1]
        filename = f"{class_name}_gesture{rep}.txt"
        file_path = os.path.join(DEST_DIR, current_user.userid, filename)
        os.makedirs(os.path.join(DEST_DIR, current_user.userid), exist_ok=True)

        try:
            with open(file_path, 'w') as file:
                for d in data['data']:
                    file.write(d)
            app.config['current_index'] += 1
            if app.config['current_index'] == len(app.config['images']):
                return render_template("final.html")
            return jsonify({"message": "Dati salvati"}), 200
        except Exception as e:
            return str(e), 500


@app.route('/')
def index():
    return redirect(url_for('login'))


PORT = 8080
HOST = "192.168.1.155"
HOST = "127.0.0.1"
IMAGES_DIR = os.path.join(os.getcwd(), 'static')
DEST_DIR = 'dataset'
NUM_CLASS = 24
NUM_REPEAT = 5

os.makedirs(DEST_DIR, exist_ok=True)

images = [f for f in os.listdir(IMAGES_DIR) if f.lower().endswith(('jpg', 'jpeg', 'png', 'gif'))]

app.config['class'] = 0
app.config['current_index'] = 0
app.config['gesture_number'] = 0


if __name__ == '__main__':
    with app.app_context():
        db.create_all()
    app.run(host=HOST, port=PORT, debug=True)
