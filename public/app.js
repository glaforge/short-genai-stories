document.addEventListener('DOMContentLoaded', function () {
        const searchParams = new URLSearchParams(window.location.search);

        try {
            let app = firebase.app();
            const db = firebase.firestore(app);

            const query = !!searchParams.get('story') ?
                db.collection('short-story').where(firebase.firestore.FieldPath.documentId(), '==', searchParams.get('story')) :
                db.collection('short-story').orderBy('createdAt', 'desc').limit(1);

            query.get()
                .then((querySnapshot) => {
                        querySnapshot.forEach((doc) => {
                            const title = doc.data().title;
                            const chapters = doc.data().chapters;

                            const publicationDate = new Date(doc.data().createdAt);

                            console.log({title, publicationDate, chapters});

                            document.querySelector("#publicationDate").innerHTML =
                                new Intl.DateTimeFormat('en-US', {dateStyle: 'long', timeZone: 'UTC'})
                                    .format(publicationDate);

                            document.querySelector("#title").innerHTML = title;

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
                                chapterContent.append(document.createTextNode(chapter.chapterContent));
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
                                            anchor.href = `?story=${storyId}`;
                                            arrow.style.display = "block";
                                        } else {
                                            arrow.style.display = "none"; // Use none instead of hidden for better semantics
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