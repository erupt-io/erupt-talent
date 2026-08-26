<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>招聘数据看板</title>
    <style>
        :root {
            --line: #e8e8e8;
            --muted: #8c8c8c;
            --brand: #1890ff;
        }

        * { box-sizing: border-box; }

        body {
            margin: 0;
            padding: 16px;
            font-family: -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif;
            font-size: 13px;
            color: #262626;
            background: #f5f6f8;
        }

        .card {
            background: #fff;
            border: 1px solid var(--line);
            border-radius: 6px;
            padding: 14px 16px;
            margin-bottom: 14px;
        }

        .card > h3 {
            margin: 0 0 12px;
            font-size: 14px;
            font-weight: 600;
        }

        .filters {
            display: flex;
            flex-wrap: wrap;
            gap: 10px;
            align-items: flex-end;
        }

        .filters label {
            display: flex;
            flex-direction: column;
            gap: 4px;
            font-size: 12px;
            color: var(--muted);
        }

        input, select, button {
            font: inherit;
            padding: 5px 8px;
            border: 1px solid #d9d9d9;
            border-radius: 4px;
            background: #fff;
            color: inherit;
        }

        button {
            cursor: pointer;
            border-color: var(--brand);
            background: var(--brand);
            color: #fff;
        }

        button.ghost {
            background: #fff;
            color: var(--brand);
        }

        .kpis {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
            gap: 10px;
        }

        .kpi {
            border: 1px solid var(--line);
            border-radius: 6px;
            padding: 10px 12px;
        }

        .kpi .label { color: var(--muted); font-size: 12px; }
        .kpi .value { font-size: 22px; font-weight: 600; margin-top: 4px; }
        .kpi .sub { color: var(--muted); font-size: 11px; margin-top: 2px; }

        .funnel { display: flex; flex-direction: column; gap: 6px; }

        .stage { display: flex; align-items: center; gap: 10px; }

        .stage .name { width: 96px; text-align: right; color: #595959; flex: none; }

        .stage .track { flex: 1; background: #f0f2f5; border-radius: 4px; overflow: hidden; }

        .stage .bar {
            height: 24px;
            background: linear-gradient(90deg, #1890ff, #40a9ff);
            border-radius: 4px;
            min-width: 2px;
            transition: width .3s;
        }

        .stage .num { width: 130px; flex: none; color: #595959; }
        .stage .num b { color: #262626; }

        table { width: 100%; border-collapse: collapse; }
        th, td { padding: 7px 8px; border-bottom: 1px solid var(--line); text-align: left; }
        th { color: var(--muted); font-weight: 500; background: #fafafa; }
        .cols { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
        @media (max-width: 860px) { .cols { grid-template-columns: 1fr; } }
        .empty { color: var(--muted); padding: 10px 0; }
    </style>
</head>
<body>

<div class="card">
    <div class="filters">
        <label>开始日期<input type="date" id="from"></label>
        <label>结束日期<input type="date" id="to"></label>
        <label>岗位<select id="jobPostId"><option value="">全部岗位</option></select></label>
        <label>渠道<select id="source"><option value="">全部渠道</option></select></label>
        <button type="button" id="query">查询</button>
        <button type="button" class="ghost" id="reset">重置</button>
        <button type="button" class="ghost" id="export">导出 CSV</button>
    </div>
</div>

<div class="card">
    <h3>核心指标</h3>
    <div class="kpis" id="kpis"></div>
</div>

<div class="card">
    <h3>招聘漏斗</h3>
    <div class="funnel" id="funnel"></div>
</div>

<div class="cols">
    <div class="card">
        <h3>各轮面试通过率</h3>
        <table>
            <thead>
            <tr><th>轮次</th><th>已评估</th><th>通过</th><th>通过率</th></tr>
            </thead>
            <tbody id="rounds"></tbody>
        </table>
    </div>
    <div class="card">
        <h3>渠道分布</h3>
        <table>
            <thead>
            <tr><th>渠道</th><th>简历数</th><th>占比</th></tr>
            </thead>
            <tbody id="channels"></tbody>
        </table>
    </div>
</div>

<div class="card">
    <h3>岗位分布</h3>
    <table>
        <thead>
        <tr><th>岗位</th><th>简历数</th><th>占比</th></tr>
        </thead>
        <tbody id="jobPosts"></tbody>
    </table>
</div>

<script>
    var TOKEN = '${token!""}' || new URLSearchParams(location.search).get('_token') || '';
    var BASE = '${base!""}';
    var SOURCES = <#if sources??>{<#list sources as k, v>"${k}": "${v}"<#sep>, </#list>}<#else>{}</#if>;

    function el(id) {
        return document.getElementById(id);
    }

    // Everything from the server is inserted as text, never as markup.
    function text(tag, value) {
        var node = document.createElement(tag);
        node.textContent = value;
        return node;
    }

    function params() {
        var query = new URLSearchParams();
        ['from', 'to', 'jobPostId', 'source'].forEach(function (key) {
            if (el(key).value) {
                query.set(key, el(key).value);
            }
        });
        return query;
    }

    function initSources() {
        Object.keys(SOURCES).forEach(function (key) {
            var option = document.createElement('option');
            option.value = key;
            option.textContent = SOURCES[key];
            el('source').appendChild(option);
        });
    }

    function renderKpis(d) {
        var items = [
            ['简历总量', d.totalResumes, '含邮箱抓取 ' + d.mailResumes + ' 份'],
            ['邮箱简历占比', d.mailRatio + '%', '邮箱自动抓取 / 简历总量'],
            ['初筛通过率', d.screenPassRate + '%', '已初筛 ' + d.screenAssessed + ' 份'],
            ['终面拟录用', d.finalProposed, '进入 Offer 环节'],
            ['Offer 发放', d.offersSent, '接受 ' + d.offersAccepted + ' / 拒绝 ' + d.offersDeclined],
            ['Offer 接受率', d.offerAcceptRate + '%', '过期 ' + d.offersExpired + ' 份'],
            ['入职人数', d.onboarded, '正式入职闭环'],
            ['到岗率', d.arrivalRate + '%', '入职人数 / Offer 接受数']
        ];
        var box = el('kpis');
        box.textContent = '';
        items.forEach(function (item) {
            var card = document.createElement('div');
            card.className = 'kpi';
            card.appendChild(text('div', item[0])).className = 'label';
            card.appendChild(text('div', item[1])).className = 'value';
            card.appendChild(text('div', item[2])).className = 'sub';
            box.appendChild(card);
        });
    }

    function renderFunnel(stages) {
        var box = el('funnel');
        box.textContent = '';
        var top = stages.length ? stages[0].value : 0;
        stages.forEach(function (stage, index) {
            var row = document.createElement('div');
            row.className = 'stage';
            row.appendChild(text('div', stage.label)).className = 'name';

            var track = document.createElement('div');
            track.className = 'track';
            var bar = document.createElement('div');
            bar.className = 'bar';
            bar.style.width = (top > 0 ? (stage.value * 100 / top) : 0) + '%';
            track.appendChild(bar);
            row.appendChild(track);

            var previous = index === 0 ? stage.value : stages[index - 1].value;
            var stepRate = previous > 0 ? (stage.value * 100 / previous).toFixed(1) : '0.0';
            var overall = top > 0 ? (stage.value * 100 / top).toFixed(1) : '0.0';
            var num = document.createElement('div');
            num.className = 'num';
            num.appendChild(text('b', stage.value));
            num.appendChild(text('span', '　环节 ' + stepRate + '% / 整体 ' + overall + '%'));
            row.appendChild(num);
            box.appendChild(row);
        });
    }

    function renderRounds(rounds) {
        var body = el('rounds');
        body.textContent = '';
        rounds.forEach(function (round) {
            var tr = document.createElement('tr');
            [round.label, round.assessed, round.passed, round.passRate + '%'].forEach(function (value) {
                tr.appendChild(text('td', value));
            });
            body.appendChild(tr);
        });
    }

    function renderSlices(id, slices) {
        var body = el(id);
        body.textContent = '';
        var total = slices.reduce(function (sum, slice) {
            return sum + slice.value;
        }, 0);
        if (!slices.length) {
            var tr = document.createElement('tr');
            var td = text('td', '暂无数据');
            td.className = 'empty';
            td.colSpan = 3;
            tr.appendChild(td);
            body.appendChild(tr);
            return;
        }
        slices.forEach(function (slice) {
            var tr = document.createElement('tr');
            var rate = total > 0 ? (slice.value * 100 / total).toFixed(1) : '0.0';
            [slice.label, slice.value, rate + '%'].forEach(function (value) {
                tr.appendChild(text('td', value));
            });
            body.appendChild(tr);
        });
    }

    function load() {
        var query = params();
        fetch(BASE + '/erupt-api/talent/dashboard?' + query.toString(), {headers: {token: TOKEN}})
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('HTTP ' + response.status);
                }
                return response.json();
            })
            .then(function (result) {
                var posts = el('jobPostId');
                if (posts.options.length <= 1) {
                    result.jobPosts.forEach(function (post) {
                        var option = document.createElement('option');
                        option.value = post.id;
                        option.textContent = post.name;
                        posts.appendChild(option);
                    });
                }
                var d = result.data;
                renderKpis(d);
                renderFunnel(d.funnel);
                renderRounds(d.rounds);
                renderSlices('channels', d.channels);
                renderSlices('jobPosts', d.jobPosts);
            })
            .catch(function (error) {
                alert('看板数据加载失败：' + error.message);
            });
    }

    el('query').addEventListener('click', load);
    el('reset').addEventListener('click', function () {
        ['from', 'to', 'jobPostId', 'source'].forEach(function (key) {
            el(key).value = '';
        });
        load();
    });
    el('export').addEventListener('click', function () {
        var query = params();
        query.set('_token', TOKEN);
        window.open(BASE + '/erupt-api/talent/dashboard/export?' + query.toString(), '_blank');
    });

    initSources();
    load();
</script>
</body>
</html>
