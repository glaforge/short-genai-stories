/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

document.addEventListener('DOMContentLoaded', function () {
        try {
            let app = firebase.app();
            const db = firebase.firestore(app);

            const searchParams = new URLSearchParams(window.location.search);
            console.log("StoryID: " + document.location.pathname.split('/').slice(-1)[0]);
            console.log("Search param: " + searchParams.get("story"));

            const storyId = searchParams.has("story") ?
                searchParams.get("story") :
                (document.location.pathname.startsWith('/story/') ?
                    document.location.pathname.split('/').slice(-1)[0] :
                    null);

            const query = !!storyId ?
                db.collection('short-story').where(firebase.firestore.FieldPath.documentId(), '==', storyId) :
                db.collection('short-story').orderBy('createdAt', 'desc').limit(1);

            query.get()
                .then((querySnapshot) => {
                        if (querySnapshot.empty) {
                            window.location.href = '/';
                        }

                        querySnapshot.forEach((doc) => {
                            const title = doc.data().title;
                            const chapters = doc.data().chapters;

                            const publicationDate = new Date(doc.data().createdAt);

                            console.log({title, publicationDate, chapters});

                            document.querySelector("#publicationDate").innerHTML =
                                new Intl.DateTimeFormat('en-US', {dateStyle: 'long', timeZone: 'UTC'})
                                    .format(publicationDate);

                            document.querySelector("#title").innerHTML = title;

                            document.title = title + " — " + document.title;

                            const chaptersElem = document.querySelector("#story div.chapters");

                            var first = false;

                            chapters.forEach((chapter) => {
                                const oneChapterDiv = document.createElement("div");

                                const details = document.createElement("details");
                                if (first === false) {
                                    details.setAttribute("open", true);
                                    first = true;
                                }

                                const summary = document.createElement("summary")
                                summary.append(document.createTextNode(chapter.chapterTitle));
                                details.append(summary);

                                const chapterContent = document.createElement("div");
                                chapterContent.innerHTML = chapter.chapterContent.replaceAll('\n\n', '<br><br>');
                                details.append(chapterContent)
                                oneChapterDiv.append(details);

                                firebase.storage(app).refFromURL(chapter.image).getDownloadURL().then((url) => {
                                    const imgTag = document.createElement("img");
                                    imgTag.setAttribute("src", url);
                                    oneChapterDiv.append(imgTag);
                                });

                                chaptersElem.append(oneChapterDiv);
                            })

                            // other stories

                            function getAdjacentStory(db, createdAt, comparator, order, selector) {
                                return db.collection('short-story')
                                    .where('createdAt', comparator, createdAt)
                                    .orderBy('createdAt', order)
                                    .limit(1)
                                    .get()
                                    .then((querySnapshot) => {
                                        const arrow = document.querySelector(selector);
                                        if (querySnapshot.docs.length > 0) {
                                            const storyId = querySnapshot.docs[0].id;
                                            console.log(selector === '#prev' ? "Previous <- " : "Next -> ", storyId);
                                            const anchor = arrow.querySelector("a");
                                            anchor.innerHTML = querySnapshot.docs[0].data().title;
                                            anchor.href = `/?story=${storyId}`;
                                            arrow.style.display = "block";
                                        } else {
                                            arrow.style.display = "none";
                                        }
                                    });
                            }

                            query.get()
                                .then((querySnapshot) => {
                                    querySnapshot.forEach((doc) => {
                                        const createdAt = doc.data().createdAt;

                                        getAdjacentStory(db, createdAt, '<', 'desc', '#prev');
                                        getAdjacentStory(db, createdAt, '>', 'asc', '#next');
                                    });
                                })
                        });
                    }
                );
        } catch (e) {
            console.error(e);
        }
    }
);